import co.ecg.alpaca.toolkit.exception.BroadWorksObjectException;
import co.ecg.alpaca.toolkit.exception.BroadWorksServerException;
import co.ecg.alpaca.toolkit.exception.HelperException;
import co.ecg.alpaca.toolkit.exception.RequestException;
import co.ecg.alpaca.toolkit.generated.Group;
import co.ecg.alpaca.toolkit.generated.ServiceProvider;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.enums.VoiceMessagingMessageProcessing;
import co.ecg.alpaca.toolkit.generated.services.UserVoiceMessaging;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.request.RequestBundler;
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool to update Voice Messaging Settings fro all User's within a Group.
 */
public class UpdateVoiceMessaging {

        public static Logger log = LogManager.getLogger(UpdateVoiceMessaging.class);

        private static final String DRYRUN = "dryrun";
        private static boolean dryrun = true;

        public static void main(String[] args) throws BroadWorksServerException, IOException, InterruptedException, HelperException, BroadWorksObjectException, RequestException {

                CommandLineParser parser = new DefaultParser();
                Options options = new Options();

                options.addOption(DRYRUN, true, "Dryrun");

                CommandLine commandLine = null;

                // Parse Command Line Options
                try {
                        commandLine = parser.parse(options, args);

                        if (commandLine.hasOption(DRYRUN)) {
                                dryrun = Boolean.parseBoolean(commandLine.getOptionValue(DRYRUN));
                        } else {
                                usage("Missing Required Parameter - " + DRYRUN);
                        }

                } catch (ParseException ex) {
                        System.err.println("Error while parsing command line - " + ex.getMessage());
                        System.exit(1);
                }

                BroadWorksServer broadWorksServer = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

                System.out.println("Dryrun: " + dryrun);

                String[] remainingArguments = commandLine.getArgs();

                List<User> userList = new ArrayList<>();

                // Check Arguments
                switch (remainingArguments.length) {
                        case 0:
                                userList = UserHelper.getAllUsersInSystem(broadWorksServer);
                                break;
                        case 2:
                                ServiceProvider serviceProvider = ServiceProvider.getPopulatedServiceProvider(broadWorksServer, remainingArguments[0]);
                                Group group = Group.getPopulatedGroup(serviceProvider, remainingArguments[1]);
                                userList = group.getUsersInGroup();
                                break;
                        default:
                                System.err.println("Provide either 0 or 2 (<serviceProviderId> <groupId>) arguments");
                                System.exit(1);
                }

                // Populate all Users in List.
                UserHelper.populateUserList(broadWorksServer, userList);
                RequestBundler bundler = broadWorksServer.getRequestBundler();

                // Iterate through Users and set VoiceMail Settings.
                RequestHelper.requestPerObjectProducer(broadWorksServer,
                        userList,
                        User.class,
                        UserVoiceMessaging.UserVoiceMessagingUserGetVoiceManagementRequest.class,
                        (user, response) -> {
                                log.debug("Processing (user, response) - " + user.getUserId() + " " + response);

                                if (response.isErrorResponse() && !response.getSummaryText().contains("[Error " + 5435 + "]")) {
                                        System.err.println("Error: " + response.getSummaryText());
                                        return;
                                }

                                if (!response.getIsActive()) System.out.printf("%25s: Voice not active\n", user.getUserId());

                                switch (response.getProcessing()) {
                                        case UNIFIEDVOICEANDEMAILMESSAGING:
                                                boolean updateSettings = false;

                                                UserVoiceMessaging.UserVoiceMessagingUserModifyVoiceManagementRequest modifyRequest = new UserVoiceMessaging.UserVoiceMessagingUserModifyVoiceManagementRequest(user);

                                                if (response.getVoiceMessageDeliveryEmailAddress().isEmpty()) {
                                                        if (!response.getVoiceMessageCarbonCopyEmailAddress().isEmpty()) {
                                                                updateSettings = true;

                                                                modifyRequest.setVoiceMessageDeliveryEmailAddress(response.getVoiceMessageCarbonCopyEmailAddress());
                                                                modifyRequest.setVoiceMessageCarbonCopyEmailAddress(null);
                                                                modifyRequest.setSendCarbonCopyVoiceMessage(false);

                                                                System.out.printf("%25s: Update voicemail processing %s using CC address - %s\n",
                                                                        user.getUserId(),
                                                                        (dryrun ? "(dryrun)" : ""),
                                                                        response.getVoiceMessageCarbonCopyEmailAddress());
                                                        } else {
                                                                System.out.printf("%25s: Failed to update processing no email address - %s\n",
                                                                        user.getUserId(),
                                                                        (dryrun ? "(dryrun)" : ""));
                                                        }
                                                } else {
                                                        updateSettings = true;

                                                        System.out.printf("%25s: Update voicemail processing %s using existing address - %s\n",
                                                                user.getUserId(),
                                                                (dryrun ? "(dryrun)" : ""),
                                                                response.getVoiceMessageDeliveryEmailAddress());

                                                        modifyRequest.setVoiceMessageCarbonCopyEmailAddress(null);
                                                        modifyRequest.setSendCarbonCopyVoiceMessage(false);
                                                }

                                                if (!dryrun && updateSettings) {
                                                        modifyRequest.setProcessing(VoiceMessagingMessageProcessing.DELIVERTOEMAILADDRESSONLY);

                                                        bundler.put(modifyRequest, modifyResponse -> {
                                                                if (modifyResponse.isErrorResponse()) System.out.println("Error: " + modifyResponse.getSummaryText());
                                                        });
                                                }
                                                break;
                                        case DELIVERTOEMAILADDRESSONLY:
                                                System.out.printf("%25s: Skipping voicemail processing - already delivering to %s\n",
                                                        user.getUserId(),
                                                        response.getVoiceMessageDeliveryEmailAddress());
                                                break;
                                }
                        });

                broadWorksServer.getRequestBundler().waitForEmptyQueue();
                System.exit(0);
        }

        /**
         * Prints the Usage Statement for the tool.
         *
         * @param message The message to print out.
         */
        public static void usage(String message) {
                System.out.println("Usage: PhoneRebootUtility --dryrun <boolean> [serviceProviderId] [groupId]");
                System.out.print("\n" + message + "\n");
                System.exit(1);
        }
}