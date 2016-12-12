import co.ecg.alpaca.toolkit.exception.BroadWorksServerException;
import co.ecg.alpaca.toolkit.exception.HelperException;
import co.ecg.alpaca.toolkit.exception.RequestException;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.enums.UserService;
import co.ecg.alpaca.toolkit.generated.services.UserHotelingGuest;
import co.ecg.alpaca.toolkit.helper.information.UserInformationBuilder;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.alpaca.toolkit.serializable.user.UserInformation;
import co.ecg.utilities.properties.P;


import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Class to Print Out a report of all Users with a Hoteling Guest Associative Host.
 *
 * Created by DavidKelley on 8/16/16.
 */
public class UsersWithHotelingGuestAssociativeHostReport {

    public static void main(String[] args) {
        try {
            // Open connection to BroadWorks
            BroadWorksServer broadWorksServer = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

            // Get All Users in the System
            List<User> users = UserHelper.getAllUsersInSystem(broadWorksServer);
            List<String> usersWithHotelingGuest = new ArrayList<>();

            // Iterate through Users and Check for Associative Host ID.
            RequestHelper.requestPerObjectProducer(broadWorksServer, users, User.class, UserHotelingGuest.UserHotelingGuestGetRequest.class, (user, response) -> {
                if (!response.isErrorResponse() && response.getHostUserId() != null) {
                    usersWithHotelingGuest.add(response.getHostUserId());
                    System.out.println("Added User: " + response.getHostUserId());
                }
            });

            // Print out totals
            System.out.println("Hoteling Guest Associated Hosts ( " + usersWithHotelingGuest.size() + " Out Of: " + users.size() + "Total Users):");

            // Print Users who have a Hoteling Guest Associative Host
            if(usersWithHotelingGuest.size() > 0) {
                for (String userId : usersWithHotelingGuest.stream().distinct().collect(Collectors.toList())) {
                    System.out.println("User ID: " + userId);
                }
            } else {
                System.out.println("\tNo Users Are Hoteling Guest Associated Hosts");
            }

            System.out.println("Finished!");

            System.exit(0);
        } catch (Exception ex) {
            System.err.println("Error while generating report - " + ex.getMessage());
            System.exit(1);
        }
           
    }
}