import co.ecg.alpaca.toolkit.generated.ServiceProvider;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import co.ecg.utilities.properties.BroadWorksServerConfig;
import co.ecg.utilities.properties.TimesTenConnectionConfig;
import jline.console.ConsoleReader;
import org.apache.commons.cli.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a clear cache command for each User in an Enterprises lineport.
 * This could be used to clear an SBC's user registration cache during migration support.
 *
 * @author Matthew Keathley
 * modified by Ashley Lee on 10/5/2017
 *  - accepts arguments for nickname and enterprise or service Provider ID
 */
public class ClearCacheReport {

    public static void main(String[] args) {
        try {

            CommandLineParser parser = new DefaultParser();

            Options options = new Options();
            options.addOption(Option.builder("e")
                    .desc("The Enterprise or Service Provider ID to generate the list of clear cache commands")
                    .longOpt("EnterpriseID")
                    .hasArg(true)
                    .required(true)
                    .build());

            options.addOption(Option.builder("c")
                    .desc("The BroadWorks cluster nickname")
                    .longOpt("clusterNickName")
                    .hasArg(true)
                    .required(false)
                    .build());

            CommandLine cmd = null;

            try {
                cmd = parser.parse(options, args);
            } catch (Exception ex) {
                new HelpFormatter().printHelp("ClearCacheReport", options);
                System.exit(1);
            }

            // Open the connection to BroadWorks
            // If Option c was provided with a valid nickname
            BroadWorksServer bws = null;

            if (cmd.hasOption("c")) {
                List<BroadWorksServerConfig> broadWorksServerConfigList = P.getProperties().getBroadWorksServerConfigList();

                for (BroadWorksServerConfig bwsConfig: broadWorksServerConfigList) {
                    if (cmd.getOptionValue("c").equals(bwsConfig.getNickname())) {
                        bws = BroadWorksServer.getBroadWorksServer(bwsConfig);
                        break;
                    }
                }

                // Confirm nickname matched a BroadWorks server in the configuration bws was loaded from the provided nickname
                if (bws == null) {
                    System.out.println(cmd.getOptionValue("c") + " does not match a server in the list of configured BroadWorks servers.");
                    new HelpFormatter().printHelp("ClearCacheReport", options);
                    System.exit(1);
                }
            } else {
                bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());
            }

            // No BroadWorks server is listed in the configuration
            if (bws == null) {
                System.out.println("The Alpaca Configuration does not contain a BroadWorks Server.  Update Configuration and try again");
                new HelpFormatter().printHelp("ClearCacheReport", options);
                System.exit(1);
            }

            // Retrieve Service Provider
            ServiceProvider serviceProvider = ServiceProvider.getPopulatedServiceProvider(bws, cmd.getOptionValue("e"));

            // Get list of Users in Service Provider
            List<User> userList = serviceProvider
                    .getGroupsInServiceProvider()
                    .stream()
                    .flatMap(group -> group.getUsersInGroup().stream())
                    .collect(Collectors.toList());

            // Perform a UserGetRequest per User in the list
            UserHelper.populateUserList(bws, userList);

            // Output clear-cache lines
            for (User user : userList) {
                if (user.getAccessDeviceEndpoint() != null) {
                    System.out.println("clear-cache " + user.getAccessDeviceEndpoint().getLinePort());
                }
            }

            System.exit(0);
        } catch (Exception ex) {
            System.out.println("Error while generating clear-cache report - " + ex.getMessage());
            System.exit(1);
        }
    }
}