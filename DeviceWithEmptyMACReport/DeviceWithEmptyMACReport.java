import co.ecg.alpaca.toolkit.generated.SystemAccessDevice.SystemAccessDeviceGetAllRequest;
import co.ecg.alpaca.toolkit.generated.SystemAccessDevice.SystemAccessDeviceGetAllResponse;
import co.ecg.alpaca.toolkit.generated.tables.SystemAccessDeviceAccessDeviceTableRow;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import co.ecg.alpaca.toolkit.generated.SystemAccessDevice;

/**
 * Tool to retrieve all display all Access Devices in the BroadWorks system
 * without a specific MAC address.
 *
 * @author Matthew Keathley
 */
public class DeviceWithEmptyMACReport {

    public static void main(String[] args) {
        try {
            // Open the connection to BroadWorks
            BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

            // Create Request
            SystemAccessDevice.SystemAccessDeviceGetAllRequest accessDeviceGetAllRequest = new SystemAccessDevice.SystemAccessDeviceGetAllRequest(bws);

            // Perform a synchronous fire to receive Response
            SystemAccessDevice.SystemAccessDeviceGetAllResponse accessDeviceGetAllResponse = accessDeviceGetAllRequest.fire();

            // Check if the Response is an Error
            if (accessDeviceGetAllResponse.isErrorResponse()) {
                System.out.println("Error while retrieving devices - " + accessDeviceGetAllResponse.getSummaryText());
                System.exit(1);
            }

            // Iterate over the Table Rows
            for (SystemAccessDeviceAccessDeviceTableRow r : accessDeviceGetAllResponse.getAccessDeviceTable()) {
                // Print the basic device information if the MAC address is empty
                if (r.getMACAddress().isEmpty()) {
                    System.out.println(
                        r.getServiceProviderId() + "\t" + r.getGroupId() + "\t" + r.getDeviceName() + "\t"
                            + r.getDeviceType());
                }
            }

            System.exit(0);
        } catch (Exception ex) {
            System.out.println("Error while generating empty MAC device report - " + ex.getMessage());
            System.exit(1);
        }
    }
}
