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
import java.util.List;

public class UpdateVoiceMessaging {

    private static final String DRYRUN = "dryrun";
    public static Logger log = LogManager.getLogger(UpdateVoiceMessaging.class);
    private static boolean dryrun = true;

    public static void main(String[] args)
        throws BroadWorksServerException, IOException, InterruptedException, HelperException, BroadWorksObjectException,
        RequestException {

        CommandLineParser parser = new PosixParser();
        Options options = new Options();

        options.addOption(DRYRUN, true, "Dryrun");

        CommandLine cmd = null;

        try {

            cmd = parser.parse(options, args);

            if (cmd.hasOption(DRYRUN)) {
                dryrun = Boolean.parseBoolean(cmd.getOptionValue(DRYRUN));
            } else {
                usage("Missing Required Parameter - " + DRYRUN);
            }

        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            System.exit(1);
        }

        BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

        System.out.println("Dryrun: " + dryrun);

        String[] remainingArguments = cmd.getArgs();

        List<User> userList;

        switch (remainingArguments.length) {
        case 0:
            userList = UserHelper.getAllUsersInSystem(bws);
            break;

        case 2:
            ServiceProvider sp = ServiceProvider.getPopulatedServiceProvider(bws, remainingArguments[0]);

            Group g = Group.getPopulatedGroup(sp, remainingArguments[1]);

            userList = g.getUsersInGroup();

            break;

        default:
            throw new HelperException("Provide either 0 or 2 (<serviceProviderId> <groupId>) arguments");
        }

        UserHelper.populateUserList(bws, userList);

        RequestBundler bundler = bws.getRequestBundler();

        RequestHelper.requestPerObjectProducer(bws,
            userList,
            User.class,
            UserVoiceMessaging.UserVoiceMessagingUserGetVoiceManagementRequest.class,
            (u, response) -> {

                User user = (User) u;

                log.debug("Processing (user, response) - " + user + " " + response);

                if (response.isErrorResponse()) {
                    if (!response.getSummaryText().contains("[Error " + 5435 + "]")) {
                        System.out.println("Error: " + response.getSummaryText());
                    }
                    return;
                }

                if (!response.getIsActive()) {
                    System.out.printf("%25s: Voice not active\n", user.getUserId());
                }

                switch (response.getProcessing()) {

                case UNIFIEDVOICEANDEMAILMESSAGING:

                    boolean updateSettings = false;

                    UserVoiceMessaging.UserVoiceMessagingUserModifyVoiceManagementRequest
                        modifyRequest =
                        new UserVoiceMessaging.UserVoiceMessagingUserModifyVoiceManagementRequest(user);

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
                            System.out.printf("%25s: Failed to update processing no email address\n",
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

                        try {
                            bundler.put(modifyRequest, modifyResponse -> {

                                if (modifyResponse.isErrorResponse()) {
                                    System.out.println("Error: " + modifyResponse.getSummaryText());
                                }

                            });

                        } catch (RequestException ex) {
                            System.out.println("Exception: " + ex.getMessage());
                        }

                    }

                    break;

                case DELIVERTOEMAILADDRESSONLY:
                    System.out.printf("%25s: Skipping voicemail processing - already delivering to %s\n",
                        (user != null ? user.getUserId() : null),
                        (response != null ? response.getVoiceMessageDeliveryEmailAddress() : null));
                    break;
                }
            });

        bws.getRequestBundler().waitForEmptyQueue();

        System.exit(0);

    }

    public static void usage(String message) {
        System.out.println("Usage: PhoneRebootUtility --dryrun <boolean> [serviceProviderId] [groupId]");
        System.out.print("\n" + message + "\n");
        System.exit(1);
    }

}
