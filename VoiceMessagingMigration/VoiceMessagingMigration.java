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
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.messaging.response.DefaultResponse;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * A tool to Migrate a Groups VoiceMail to a new Domain.
 */
public class VoiceMessagingMigration {

    public static Logger log = LogManager.getLogger(VoiceMessagingMigration.class);

    private static final String DRYRUN = "dryrun";
    private static final String SOURCE = "sourceDomain";
    private static final String DESTINATION = "destinationDomain";
    private static final String MAILSERVER = "mailServer";
    private static boolean dryrun = true;

    public static void main(String[] args) throws BroadWorksServerException, IOException, InterruptedException, HelperException, BroadWorksObjectException, RequestException {

        // Build Command Line and Options
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        Option dryrunOption = Option.builder("D").longOpt(DRYRUN).required(true).hasArg().valueSeparator().build();
        Option sourceOption = Option.builder("s").longOpt(SOURCE).required(true).hasArg().valueSeparator().build();

        Option destinationOption = Option.builder("d").longOpt(DESTINATION).required(true).hasArg().valueSeparator().build();
        Option mailServerOption = Option.builder("m").longOpt(MAILSERVER).required(true).hasArg().valueSeparator().build();

        options.addOption(dryrunOption);
        options.addOption(sourceOption);
        options.addOption(destinationOption);
        options.addOption(mailServerOption);

        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            System.err.println("An error occurred while parsing the command line - " + ex.getMessage());
            System.exit(1);
        }

        BroadWorksServer broadWorksServer = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

        final String sourceDomain = cmd.getOptionValue(SOURCE);
        final String destinationDomain = cmd.getOptionValue(DESTINATION);
        dryrun = Boolean.valueOf(cmd.getOptionValue(DRYRUN));

        System.out.println("Dryrun: " + dryrun);

        // Check Arguments
        String[] remainingArguments = cmd.getArgs();
        if (remainingArguments.length != 2) {
            printUsage("Missing Arguments");
        }

        ServiceProvider serviceProvider = ServiceProvider.getPopulatedServiceProvider(broadWorksServer, remainingArguments[0]);
        Group group = Group.getPopulatedGroup(serviceProvider, remainingArguments[1]);

        // Get All Users within Group And Populate
        List<User> userList = group.getUsersInGroup();
        UserHelper.populateUserList(broadWorksServer, userList);

        // Fire VoiceMail Request
        GroupVoiceMessaging.GroupVoiceMessagingGroupGetResponse groupVoiceMessagingGetResponse = new GroupVoiceMessaging.GroupVoiceMessagingGroupGetRequest(group).fire();

        switch (groupVoiceMessagingGetResponse.getUseMailServerSetting()) {
            case SYSTEMMAILSERVER:
                // Group uses System Mail Server -- Nothing to do here
                System.out.println("Group is set to use the system mail server");
                System.exit(1);
            case GROUPMAILSERVER:
                // Group uses Group Mail Server
                if (!dryrun) {
                    // Set new Mail Server Address
                    GroupVoiceMessaging.GroupVoiceMessagingGroupModifyRequest groupVoiceMessagingGroupModifyRequest = new GroupVoiceMessaging.GroupVoiceMessagingGroupModifyRequest(group);
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

        // Iterate through all Users in Group and update Voicemail Settings.
        RequestHelper.requestPerObjectProducer(broadWorksServer,
                userList,
                User.class,
                UserVoiceMessaging.UserVoiceMessagingUserGetVoiceManagementRequest.class,
                (user, response) -> {
                    log.debug("Processing (user, response) - " + user + " " + response);

                    if (response.isErrorResponse()) {
                        if (!response.getSummaryText().contains("[Error " + 5435 + "]")) {
                            System.err.println("Error: " + response.getSummaryText());
                        }
                        return;
                    }
                    if (!response.getIsActive()) {
                        System.out.printf("%25s: Voice Messaging not active\n", user.getUserId());
                        return;
                    }

                    // Check VoiceMail Procesing
                    switch (response.getProcessing()) {
                        case UNIFIEDVOICEANDEMAILMESSAGING:
                            // Modify Email Address
                            UserVoiceMessaging.UserVoiceMessagingUserGetAdvancedVoiceManagementResponse userAdvanceVoiceMessagingGetResponse =
                                    new UserVoiceMessaging.UserVoiceMessagingUserGetAdvancedVoiceManagementRequest(user).fire();

                            if (userAdvanceVoiceMessagingGetResponse.isErrorResponse()) {
                                System.err.println("Request failed - " + user.getUserId() + " - " + userAdvanceVoiceMessagingGetResponse.getSummaryText());
                                return;
                            }
                            if (!userAdvanceVoiceMessagingGetResponse.getMailServerSelection().equals(VoiceMessagingUserMailServerSelection.GROUPMAILSERVER)) {
                                System.out.printf("%25s: User not setup for Group Mail Server\n", user.getUserId());
                                return;
                            }

                            String originalEmail = userAdvanceVoiceMessagingGetResponse.getGroupMailServerEmailAddress();
                            if (!originalEmail.contains("@" + sourceDomain)) {
                                System.out.printf("%25s: Current email address (%s) does not match expected domain (%s)\n",
                                        user.getUserId(),
                                        originalEmail,
                                        sourceDomain);
                                return;
                            }

                            String modifiedEmailAddress = originalEmail.replaceAll("@" + sourceDomain, "@" + destinationDomain);

                            // Print New Email
                            System.out.printf("%25s: In - %s  Out - %s\n",
                                    user.getUserId(),
                                    originalEmail,
                                    modifiedEmailAddress);
                            break;
                        case DELIVERTOEMAILADDRESSONLY:
                            System.out.printf("%25s: Skipping voicemail processing - already delivering to %s\n",
                                    (user != null ? user.getUserId() : null),
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
    private static void printUsage(String message) {
        System.out.println(
                "Usage: VoiceMessagingMigration --dryrun=<boolean> --" + SOURCE + "=<sourceDomain> --" + DESTINATION
                        + "=<destinationDomain> --" + MAILSERVER + "=<mailServer> [serviceProviderId] [groupId]");
        System.out.print("\n" + message + "\n");
        System.exit(1);
    }
}
