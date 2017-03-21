package co.ecg.loader;

import co.ecg.alpaca.toolkit.exception.BroadWorksObjectException;
import co.ecg.alpaca.toolkit.exception.RequestException;
import co.ecg.alpaca.toolkit.generated.Group;
import co.ecg.alpaca.toolkit.generated.Group.GroupAddRequest;
import co.ecg.alpaca.toolkit.generated.Group.GroupDeleteRequest;
import co.ecg.alpaca.toolkit.generated.Group.GroupServiceModifyAuthorizationListRequest;
import co.ecg.alpaca.toolkit.generated.GroupAccessDevice;
import co.ecg.alpaca.toolkit.generated.GroupAccessDevice.GroupAccessDeviceAddRequest;
import co.ecg.alpaca.toolkit.generated.ServiceProvider;
import co.ecg.alpaca.toolkit.generated.ServiceProvider.ServiceProviderDeleteRequest;
import co.ecg.alpaca.toolkit.generated.ServiceProvider.ServiceProviderServiceModifyAuthorizationListRequest;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.User.UserAddRequest;
import co.ecg.alpaca.toolkit.generated.User.UserDeleteRequest;
import co.ecg.alpaca.toolkit.generated.User.UserModifyRequest;
import co.ecg.alpaca.toolkit.generated.User.UserServiceAssignListRequest;
import co.ecg.alpaca.toolkit.generated.datatypes.AccessDeviceMultipleContactEndpointModify;
import co.ecg.alpaca.toolkit.generated.datatypes.Endpoint;
import co.ecg.alpaca.toolkit.generated.datatypes.UnboundedPositiveInt;
import co.ecg.alpaca.toolkit.generated.datatypes.UserServiceAuthorization;
import co.ecg.alpaca.toolkit.generated.enums.UserService;
import co.ecg.alpaca.toolkit.generated.services.UserAuthentication;
import co.ecg.alpaca.toolkit.messaging.response.Response;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An example stand alone Alpaca Tool
 *
 * @author Matthew Keathley on 2/23/17.
 */
public class Example {

    private static final Logger log = LogManager.getLogger(BulkLoader.class);

    /**
     * The Example entry point
     *
     * @param args The passed arguments
     */
    public static void main(String[] args) {
        try {
            // Create BroadWorks Connection
            BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

            if (bws == null) {
                log.fatal("Connection to BroadWorks failed");
                System.exit(1);
            }

            System.out.println();

            System.exit(0);
        } catch (Exception ex) {
            String error = "Error while processing provisioning file - ";
            log.fatal(error, ex);
            System.out.println(error + ex.getMessage());
            System.exit(1);
        }
    }
}
