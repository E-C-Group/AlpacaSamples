import co.ecg.alpaca.toolkit.config.PrintColor;
import co.ecg.alpaca.toolkit.config.PrintColorWriter;
import co.ecg.alpaca.toolkit.exception.BroadWorksObjectException;
import co.ecg.alpaca.toolkit.exception.HelperException;
import co.ecg.alpaca.toolkit.generated.GroupAccessDevice;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.datatypes.AccessDeviceEndpointAdd;
import co.ecg.alpaca.toolkit.generated.datatypes.DeviceManagementUserNamePassword16;
import co.ecg.alpaca.toolkit.generated.services.GroupIntegratedIMP;
import co.ecg.alpaca.toolkit.generated.services.ServiceProviderIntegratedIMP;
import co.ecg.alpaca.toolkit.generated.services.UserIntegratedIMP;
import co.ecg.alpaca.toolkit.generated.services.UserSharedCallAppearance;
import co.ecg.alpaca.toolkit.generated.tables.GroupServiceServicePacksAuthorizationTableRow;
import co.ecg.alpaca.toolkit.helper.information.GroupInformationBuilder;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.response.DefaultResponse;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.alpaca.toolkit.serializable.group.GroupInformation;
import co.ecg.utilities.properties.P;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Assigns specified Services for UCOne to a user and configures the user's settings.
 * <p>
 * Created by alee on 3/2/17.
 * Edited by dkelley on 3/17/17
 * <p>
 * Contact - support@e-c-group.com
 */
public class UCOneProvisioningTool {

    private static BroadWorksServer broadWorksServer;
    private static User user;
    private static GroupInformation groupInformation;
    private static PrintColorWriter out;

    // Service Packs Go Here
    private static final String[] SERVICEPACKS = {"ServicePack1", "ServicePack2"};
    private static final HashMap<String, String> deviceTypeToNameMap = new HashMap<>();

    public static void main(String[] args) {
        try {
            // Confirm userId was provided
            if (args.length != 1) {
                usage("Please provide the BroadWorks UserId for the user to provision UCOne under.");
            }

            out = new PrintColorWriter(System.out);

            out.println(PrintColor.BLUE, " -- Alpaca UCOne Provisioning Script -- ");
            out.println(PrintColor.BLUE, " * Attempting to connect to BroadWorks");

            //Open the connection to BroadWorks
            broadWorksServer = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());
            if (broadWorksServer == null) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " Could not establish connection to BroadWorks. Please check properties file.");
                System.exit(1);
            }
            out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " Successfully connected to BroadWorks.");

            out.println(PrintColor.BLUE, " * Attempting to retrieve " + args[0]);

            //Load the user
            try {
                user = User.getPopulatedUser(broadWorksServer, args[0]);
                if (user.getPhoneNumber() == null || user.getPhoneNumber().isEmpty()) {
                    out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " User does not have a phone number. Cannot continue.");
                    System.exit(1);
                }

                deviceTypeToNameMap.put("Business Communicator - PC", user.getPhoneNumber() + "-PC");
                deviceTypeToNameMap.put("Business Communicator - Mobile", user.getPhoneNumber() + "-Mobile");
                deviceTypeToNameMap.put("Business Communicator - Tablet", user.getPhoneNumber() + "-Tablet");
                deviceTypeToNameMap.put("Connect - Mobile", user.getPhoneNumber() + "-Connect");

            } catch (BroadWorksObjectException ex) {
                // User not found. Exit.
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " User " + args[0] + " cannot be found in the system.");
                System.exit(1);
            }

            out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " Successfully retrieved " + args[0]);

            groupInformation = new GroupInformationBuilder(user.getGroup()).addGroupServices().execute();
            if (groupInformation == null || groupInformation.getGroupServices() == null) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " Unable to retrieve Group Information");
                System.exit(1);
            }

            // Perform Checks
            performChecks();

            out.println(PrintColor.BLUE, " * Assigning Service Packs");
            // Assign Service Packs
            User.UserServiceAssignListRequest assignListRequest = new User.UserServiceAssignListRequest(user);
            assignListRequest.setServicePackName(SERVICEPACKS);
            DefaultResponse assignListResponse = assignListRequest.fire();
            if (assignListResponse.isErrorResponse()) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while assigning service packs: \n" + assignListResponse.getSummaryText());
                System.exit(1);
            }
            out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " Successfully assigned Service Packs");

            out.println(PrintColor.BLUE, " * Assigning Integrated IM&P");
            //Enable Integrated IM&P
            UserIntegratedIMP.UserIntegratedIMPModifyRequest integratedIMPModifyRequest = new UserIntegratedIMP.UserIntegratedIMPModifyRequest(user);
            integratedIMPModifyRequest.setIsActive(true);
            DefaultResponse integratedIMPModifyResponse = integratedIMPModifyRequest.fire();
            if (integratedIMPModifyResponse.isErrorResponse()) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while enabling Integrated IM&P. \n" + integratedIMPModifyResponse.getSummaryText());
                System.exit(1);

            }
            out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " Successfully assigned Integrated IM&P.");

            // Apply SCA Settings
            out.println(PrintColor.BLUE, " * Applying SCA Settings.");
            UserSharedCallAppearance.UserSharedCallAppearanceModifyRequest sharedCallAppearanceModifyRequest = new UserSharedCallAppearance.UserSharedCallAppearanceModifyRequest(user);
            sharedCallAppearanceModifyRequest.setAlertAllAppearancesForClickToDialCalls(true);
            sharedCallAppearanceModifyRequest.setAllowSCACallRetrieve(true);
            sharedCallAppearanceModifyRequest.setMultipleCallArrangementIsActive(true);
            sharedCallAppearanceModifyRequest.setEnableCallParkNotification(true);

            DefaultResponse sharedCallAppearanceModifyResponse = sharedCallAppearanceModifyRequest.fire();
            if(sharedCallAppearanceModifyResponse.isErrorResponse()) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while applying SCA settings. \n" + sharedCallAppearanceModifyResponse.getSummaryText());
            }
            out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " Successfully applied SCA settings.");

            // Assign Devices
            out.println(PrintColor.BLUE, " * Assigning Devices.");
            deviceTypeToNameMap.keySet().forEach(device -> {
                addDevice(deviceTypeToNameMap.get(device), device);
                setupSCA(deviceTypeToNameMap.get(device));
            });
            out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " Successfully assigned Devices.");

            /// / Exit the program successfully
            System.out.println("User - " + user.getUserId() + " has been setup successfully.");
            System.exit(0);
        } catch (Exception ex) {
            out.println(PrintColor.RED, "Error while attempting to provision UC-One for user - " + args[0]);
            System.exit(1);
        }
    }

    /**
     * Method to perform pre-checks before applying settings.
     */
    private static void performChecks() {
        List<String> errors = new ArrayList<>();
        out.println(PrintColor.BLUE, " * Performing Checks");

        // Check if Service Packs are authorized
        for (String servicePackName : SERVICEPACKS) {
            if (groupInformation.getGroupServices().getServicePackAuthorizationList() != null) {
                Optional<GroupServiceServicePacksAuthorizationTableRow> servicePack = groupInformation.getGroupServices().getServicePackAuthorizationList().stream().filter(pack -> pack.getServicePackName().equals(servicePackName)).findFirst();
                if (servicePack.isPresent() && servicePack.get().getAuthorized().equals("true")) {
                    if (servicePack.get().getLimited().equals("true")) {
                        int allocated = Integer.getInteger(servicePack.get().getAllocated());
                        int usage = Integer.getInteger(servicePack.get().getUsage());

                        if (allocated - usage < 1) {
                            errors.add("Service Pack - " + servicePackName + " - is authorized to the Group, but there are not enough allocated.\n" +
                                    "Allocated: " + allocated + "\n" +
                                    "Usage: " + usage);
                        }
                    }
                } else {
                    errors.add("Service Pack - " + servicePackName + " - is not authorized to the Group.");
                }
            }
        }

        // Check if Group or ServiceProvider have provided an Integrated IM&P domain.
        Optional<GroupIntegratedIMP> groupIntegratedIMP = groupInformation.getGroupUserServices().stream()
                .filter(GroupIntegratedIMP.class::isInstance)
                .map(GroupIntegratedIMP.class::cast)
                .findFirst();

        if (!groupIntegratedIMP.isPresent() ||
                groupIntegratedIMP.get().getIntegratedIMP() == null ||
                groupIntegratedIMP.get().getIntegratedIMP().getServiceDomain() == null ||
                groupIntegratedIMP.get().getIntegratedIMP().getServiceDomain().isEmpty()) {

            ServiceProviderIntegratedIMP.ServiceProviderIntegratedIMPGetResponse serviceProviderIntegratedIMPGetResponse = new ServiceProviderIntegratedIMP.ServiceProviderIntegratedIMPGetRequest(user.getServiceProvider()).fire();
            if (serviceProviderIntegratedIMPGetResponse.isErrorResponse() ||
                    serviceProviderIntegratedIMPGetResponse.getServiceDomain() == null ||
                    serviceProviderIntegratedIMPGetResponse.getServiceDomain().isEmpty()) {
                errors.add("An Integrated IM&P service domain has not been provided at the Group or Service Provider level.");
            }
        }

        if (!errors.isEmpty()) {
            errors.forEach(error -> {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " " + error);
            });

            System.exit(1);
        }

        out.println(PrintColor.GREEN, "\t" + PrintColorWriter.CHECK_MARK + " All checks passed successfully.");
    }

    /**
     * Prints the Usage Statement for the tool.
     *
     * @param message The message to print out.
     */
    public static void usage(String message) {
        System.out.print("Usage: ./runner UCOneProvisioningTool <userId>");
        System.out.print("\n" + message + "\n");
        System.exit(1);
    }

    /**
     * Method to add a {@link GroupAccessDevice}.
     *
     * @param deviceName The name of the device to add.
     * @param deviceType The type of the device to add.
     */
    private static void addDevice(String deviceName, String deviceType) {
        DefaultResponse deviceAdd = new GroupAccessDevice.GroupAccessDeviceAddRequest(broadWorksServer, user.getServiceProviderId(), user.getGroupId(), deviceName, deviceType).fire();
        if (deviceAdd.isErrorResponse()) {
            out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while adding device: " + deviceName + "\n" + deviceAdd.getSummaryText());
            System.exit(1);
        }
    }

    /**
     * Method to assign an SCA device to the User and setup custom credentials.
     *
     * @param deviceName The name of the device to assign as an SCA.
     */
    private static void setupSCA(String deviceName) {
        try {
            GroupAccessDevice groupAccessDevice = GroupAccessDevice.getPopulatedGroupAccessDevice(user.getGroup(), deviceName);
            AccessDeviceEndpointAdd accessDeviceEndpointAdd = new AccessDeviceEndpointAdd(groupAccessDevice, groupAccessDevice.getDeviceName() + "@" + user.getGroup().getDefaultDomain());

            DefaultResponse endpointAdd = new UserSharedCallAppearance.UserSharedCallAppearanceAddEndpointRequest(user, accessDeviceEndpointAdd, true, true, true).fire();
            if (endpointAdd.isErrorResponse()) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while adding an SCA: " + endpointAdd.getSummaryText());
                System.exit(1);
            }

            GroupAccessDevice.GroupAccessDeviceModifyRequest groupAccessDeviceModifyRequest = new GroupAccessDevice.GroupAccessDeviceModifyRequest(groupAccessDevice);
            DeviceManagementUserNamePassword16 credentials = new DeviceManagementUserNamePassword16(user.getUserId(), UserHelper.generateRandomUserPassword(user));
            groupAccessDeviceModifyRequest.setUseCustomUserNamePassword(true);
            groupAccessDeviceModifyRequest.setAccessDeviceCredentials(credentials);

            DefaultResponse groupAccessDeviceModifyResponse = groupAccessDeviceModifyRequest.fire();
            if (groupAccessDeviceModifyResponse.isErrorResponse()) {
                out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while setting up device credentials: " + groupAccessDeviceModifyResponse.getSummaryText());
                System.exit(1);
            }
        } catch (BroadWorksObjectException | HelperException ex) {
            out.println(PrintColor.RED, "\t" + PrintColorWriter.ERROR_MARK + " An error occurred while setting up the SCA: " + ex);
            System.exit(1);
        }
    }
}
