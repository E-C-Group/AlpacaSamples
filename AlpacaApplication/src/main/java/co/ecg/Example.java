package co.ecg;

import co.ecg.alpaca.toolkit.generated.BWSystem;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An example stand alone Alpaca Tool
 *
 * @author Matthew Keathley on 2/23/17.
 */
public class Example {

    private static final Logger log = LogManager.getLogger(Example.class);

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

            StringBuilder information = new StringBuilder();

            information.append(String.format("%1$-30s %2$s \n", "BroadWorks Address: ", bws.getServerAddress()));
            information.append(String.format("%1$-30s %2$s \n", "Logged In User: ", bws.getUsername()));

            information.append("\n");

            BWSystem.SystemLicensingGetRequest licensingRequest = new BWSystem.SystemLicensingGetRequest(bws);
            BWSystem.SystemLicensingGetResponse licensingResponse = licensingRequest.fire();

            if (!licensingResponse.isErrorResponse()) {
                information.append(String.format("%1$-30s %2$-55s \n", "", "Licensing").replaceAll(" ", "-"));
                information.append(String.format("%1$-30s %2$s \n", "License Strictness: ", licensingResponse.getLicenseStrictness()));
                information.append(String.format("%1$-30s %2$s \n", "Group User Limit: ", licensingResponse.getGroupUserlimit()));
                information.append(String.format("%1$-30s %2$s \n", "Expiration Date: ", licensingResponse.getExpirationDate()));

                String[] hostIdList = licensingResponse.getHostId();
                for (String element : hostIdList) {
                    information.append(String.format("%1$-30s %2$s \n", "Host ID: ", element));
                }

                String[] licenseList = licensingResponse.getLicenseName();
                for (String element : licenseList) {
                    information.append(String.format("%1$-30s %2$s \n", "License Name: ", element));
                }

                information.append(String.format("%1$-30s %2$s \n", "Number of Trunk Users: ", licensingResponse.getNumberOfTrunkUsers()));
            }

            System.out.println(information.toString());
            System.exit(0);
        } catch (Exception ex) {
            log.fatal(ex);
            System.out.println(ex.getMessage());
            System.exit(1);
        }
    }
}
