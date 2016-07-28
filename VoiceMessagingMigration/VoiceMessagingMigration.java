import co.ecg.alpaca.toolkit.exception.BroadWorksObjectException;
import co.ecg.alpaca.toolkit.exception.BroadWorksServerException;
import co.ecg.alpaca.toolkit.exception.HelperException;
import co.ecg.alpaca.toolkit.exception.RequestException;
import co.ecg.alpaca.toolkit.generated.Group;
import co.ecg.alpaca.toolkit.generated.ServiceProvider;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.enums.VoiceMessagingUserMailServerSelection;
import co.ecg.alpaca.toolkit.generated.services.GroupVoiceMessaging;
import co.ecg.alpaca.toolkit.generated.services.UserVoiceMessaging;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.request.RequestBundler;
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.messaging.response.DefaultResponse;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class VoiceMessagingMigration {

    private static final String DRYRUN = "dryrun";
    private static final String SOURCE = "sourceDomain";
    private static final String DESTINATION = "destinationDomain";
    private static final String MAILSERVER = "mailServer";
    public static Logger log = LogManager.getLogger(VoiceMessagingMigration.class);
    private static boolean dryrun = true;

    public static void main(String[] args)
        throws BroadWorksServerException, IOException, InterruptedException, HelperException, BroadWorksObjectException,
        RequestException {

        CommandLineParser parser = new PosixParser();
        Options options = new Options();

        Option dryrunOption = Option.builder("D").longOpt(DRYRUN).required(true).hasArg().valueSeparator().build();
        Option sourceOption = Option.builder("s").longOpt(SOURCE).required(true).hasArg().valueSeparator().build();

        Option
            destinationOption =
            Option.builder("d").longOpt(DESTINATION).required(true).hasArg().valueSeparator().build();

        Option
            mailServerOption =
            Option.builder("m").longOpt(MAILSERVER).required(true).hasArg().valueSeparator().build();

        options.addOption(dryrunOption);
        options.addOption(sourceOption);
        options.addOption(destinationOption);
        options.addOption(mailServerOption);

        CommandLine cmd = null;

        try {

            cmd = parser.parse(options, args);

        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            System.exit(1);
        }

        BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

        final String sourceDomain = cmd.getOptionValue(SOURCE);
        final String destinationDomain = cmd.getOptionValue(DESTINATION);

        System.out.println("Dryrun: " + dryrun);

        String[] remainingArguments = cmd.getArgs();

        if (remainingArguments.length != 2) {
            printUsage("Missing Arguments");
        }

        ServiceProvider sp = ServiceProvider.getPopulatedServiceProvider(bws, remainingArguments[0]);

        Group g = Group.getPopulatedGroup(sp, remainingArguments[1]);

        List<User> userList = g.getUsersInGroup();

        UserHelper.populateUserList(bws, userList);

        RequestBundler bundler = bws.getRequestBundler();

        GroupVoiceMessaging.GroupVoiceMessagingGroupGetRequest
            groupGetRequest =
            new GroupVoiceMessaging.GroupVoiceMessagingGroupGetRequest(g);
        GroupVoiceMessaging.GroupVoiceMessagingGroupGetResponse groupGetResponse = groupGetRequest.fire();

        switch (groupGetResponse.getUseMailServerSetting()) {
        case SYSTEMMAILSERVER:
            System.out.println("Group is set to use the system mail server");
            System.exit(1);

        case GROUPMAILSERVER:

            if (!dryrun) {

                GroupVoiceMessaging.GroupVoiceMessagingGroupModifyRequest
                    groupVoiceMessagingGroupModifyRequest =
                    new GroupVoiceMessaging.GroupVoiceMessagingGroupModifyRequest(g);

                groupVoiceMessagingGroupModifyRequest.setMailServerNetAddress(cmd.getOptionValue(MAILSERVER));

                DefaultResponse groupVoiceMessagingGroupModifyResponse = groupVoiceMessagingGroupModifyRequest.fire();

                if (groupVoiceMessagingGroupModifyResponse.isErrorResponse()) {
                    System.out.println("Unable to change the group mail server");
                    System.exit(1);
                }

                break;
            }
        }

        System.out.println("Users: " + userList.size());

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
                    System.out.printf("%25s: Voice Messaging not active\n", user.getUserId());
                    return;
                }

                switch (response.getProcessing()) {

                case UNIFIEDVOICEANDEMAILMESSAGING:

                    boolean updateSettings = false;

                    try {

                        UserVoiceMessaging.UserVoiceMessagingUserGetAdvancedVoiceManagementRequest
                            getAVMRequest =
                            new UserVoiceMessaging.UserVoiceMessagingUserGetAdvancedVoiceManagementRequest(user);

                        UserVoiceMessaging.UserVoiceMessagingUserGetAdvancedVoiceManagementResponse
                            getAVMResponse =
                            getAVMRequest.fire();

                        if (getAVMResponse.isErrorResponse()) {
                            System.out.println(
                                "Request failed - " + user.getUserId() + " - " + getAVMResponse.getSummaryText());
                            return;
                        }

                        if (!getAVMResponse.getMailServerSelection()
                            .equals(VoiceMessagingUserMailServerSelection.GROUPMAILSERVER)) {
                            System.out.printf("%25s: User not setup for Group Mail Server\n", user.getUserId());
                            return;
                        }

                        String originalEmail = getAVMResponse.getGroupMailServerEmailAddress();

                        if (!originalEmail.contains("@" + sourceDomain)) {
                            System.out.printf("%25s: Current email address (%s) does not match expected domain (%s)\n",
                                user.getUserId(),
                                originalEmail,
                                sourceDomain);
                            return;
                        }

                        String
                            modifiedEmailAddress =
                            originalEmail.replaceAll("@" + sourceDomain, "@" + destinationDomain);

                        System.out.printf("%25s: In - %s  Out - %s\n",
                            user.getUserId(),
                            originalEmail,
                            modifiedEmailAddress);

                    } catch (RequestException e) {
                        e.printStackTrace();
                    }

                    if (!dryrun) {

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

    public static void printUsage(String message) {
        System.out.println(
            "Usage: VoiceMessagingMigration --dryrun=<boolean> --" + SOURCE + "=<sourceDomain> --" + DESTINATION
                + "=<destinationDomain> --" + MAILSERVER + "=<mailServer> [serviceProviderId] [groupId]");
        System.out.print("\n" + message + "\n");
        System.exit(1);
    }

}
