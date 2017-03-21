import co.ecg.alpaca.toolkit.generated.ServiceProvider;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a clear cache command for each User in an Enterprises lineport.
 * This could be used to clear an SBC's user registration cache during migration support.
 *
 * @author Matthew Keathley
 */
public class ClearCacheReport {

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Please specify Enterprise or ServiceProvider ID.");
                System.exit(1);
            }

            // Open the connection to BroadWorks
            BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

            // Retrieve Service Provider
            ServiceProvider serviceProvider = ServiceProvider.getPopulatedServiceProvider(bws, args[0]);

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