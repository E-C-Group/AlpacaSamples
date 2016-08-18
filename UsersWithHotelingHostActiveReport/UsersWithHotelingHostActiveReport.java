import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.services.UserHotelingHost;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by DavidKelley on 8/18/16.
 */
public class UsersWithHotelingHostActiveReport {

    public static void main(String[] args) {
        try {
            BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());
            List<User> users = UserHelper.getAllUsersInSystem(bws);
            List<String> usersWithHotelingHost = new ArrayList<>();

            RequestHelper.requestPerObjectProducer(bws, users, User.class, UserHotelingHost.UserHotelingHostGetRequest.class, (user, response) -> {
                if(!response.isErrorResponse()) {
                    if(response.getIsActive()) {
                        usersWithHotelingHost.add(user.getUserId());
                    }
                }
            });

            System.out.println("Total Users With Hoteling Host Active: " + usersWithHotelingHost.size());

            if(usersWithHotelingHost.size() > 0) {
                for (String userId : usersWithHotelingHost.stream().distinct().collect(Collectors.toList())) {
                    System.out.println("User ID: " + userId);
                }
            } else {
                System.out.println("\tNo Users Have Hoteling Host Assigned");
            }

            System.out.println("Finished!");

            System.exit(0);
        } catch (Exception ex) {
            System.out.println("Error while generating report - " + ex.getMessage());
            System.exit(1);
        }
    }
}