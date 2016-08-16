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
 * Created by DavidKelley on 8/16/16.
 */
public class UsersWithHotelingGuestAssociativeHostReport {

    public static void main(String[] args) {
        try {
            BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());
            List<User> users = UserHelper.getAllUsersInSystem(bws);
            List<String> usersWithHotelingGuest = new ArrayList<>();

            RequestHelper.requestPerObjectProducer(bws, users, User.class, UserHotelingGuest.UserHotelingGuestGetRequest.class, (user, response) -> {
                if (!response.isErrorResponse() && response.getHostUserId() != null) {
                    usersWithHotelingGuest.add(response.getHostUserId());
                    System.out.println("Added User: " + response.getHostUserId());
                }
            });

            System.out.println("Hoteling Guest Associated Hosts ( " + usersWithHotelingGuest.size() + " Out Of: " + users.size() + "Total Users):");

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
            System.out.println("Error while generating report - " + ex.getMessage());
            System.exit(1);
        }
           
    }
}