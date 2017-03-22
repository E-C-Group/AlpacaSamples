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
 * A bulk provisioning and deprovisioning tool for BroadWorks.
 *
 * This tool accepts a TSV input file with the following fields:
 * enterpriseID,
 * groupID,
 * userID,
 * domain,
 * servicesToAssignCommaDelimited,
 * extensionDigits,
 * webPortalPassword,
 * sipUserName,
 * sipAuthenticationPassword,
 * identityDeviceProfileType,
 * identityDeviceProfileName,
 * linePort
 *
 * The provisioned users have restrictions -
 * - An extension but no assigned number.
 * - A single Group level Access Device.
 * - Authentication must be assigned.
 *
 * @author Matthew Keathley on 2/23/17.
 */
public class BulkLoader {

    private static final Logger log = LogManager.getLogger(BulkLoader.class);

    private enum Mode {Provision, Deprovision}

    /**
     * The BulkLoader entry point
     *
     * @param args The passed arguments
     */
    public static void main(String[] args) {
        try {
            System.out.println("Starting BulkLoader Script...");
            log.info("Starting BulkLoader Script");

            CommandLineParser parser = new DefaultParser();
            Options options = new Options();
            options.addOption(Option.builder("f")
                    .desc("The provisioning TSV file")
                    .longOpt("file")
                    .hasArg(true)
                    .required(true)
                    .build());

            options.addOption(Option.builder("d")
                    .desc("Deprovisions entries in the provisioning TSV file")
                    .longOpt("deprovision")
                    .hasArg(false)
                    .required(false)
                    .build());

            CommandLine cmd = null;

            try {
                cmd = parser.parse(options, args);
            } catch (Exception ex) {
                new HelpFormatter().printHelp("BulkLoader", options);
                System.exit(1);
            }

            Mode mode;
            if (cmd.hasOption("d")) mode = Mode.Deprovision;
            else mode = Mode.Provision;

            // Create BroadWorks Connection
            BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

            if (bws == null) {
                log.fatal("Connection to BroadWorks failed");
                System.exit(1);
            }

            ICsvListReader listReader = null;
            try {
                log.info("Parsing TSV File");
                listReader = new CsvListReader(new FileReader(cmd.getOptionValue("f")), CsvPreference.TAB_PREFERENCE);

                List<String> row;
                log.info("Iterating through CSV Rows...");
                while ((row = listReader.read()) != null) {
                    if (row.size() != 12) {
                        System.out.println("Ignoring row with incorrect # of columns: " + row.toString());
                        continue;
                    }

                    System.out.println("Processing Row - " + row.toString());
                    ProvisioningRow provisioningRow = new ProvisioningRow(row);

                    switch (mode) {
                        case Provision:
                            ServiceProvider serviceProvider = provisionServiceProvider(bws, provisioningRow);
                            Group group = provisionGroup(bws, serviceProvider, provisioningRow);
                            User user = provisionUser(bws, provisioningRow);
                            System.out.println("Provisioning of User: " + user.getUserId() + " complete!");
                            break;
                        case Deprovision:
                            deprovisionUser(bws, provisioningRow);
                            System.out.println("Deprovisioning of User: " + provisioningRow.getUserId() + " complete!");
                            break;
                    }
                }
            } finally {
                if (listReader != null) {
                    listReader.close();
                }
            }

            System.out.println("Process Complete!");
            System.exit(0);
        } catch (Exception ex) {
            String error = "Error while processing provisioning file - ";
            log.fatal(error, ex);
            System.out.println(error + ex.getMessage());
            System.exit(1);
        }
    }


    /**
     * Provisions the ServiceProvider for the ProvisioningRow. If the ServiceProvider does not exist then it is added.
     * The services for this user are authorized.
     *
     * @param bws             The BroadWorks Server
     * @param provisioningRow The ServiceProvider ID
     * @return ServiceProvider The provisioned ServiceProvider object
     * @throws RequestException          Thrown if an error occurs while firing a request.
     * @throws BroadWorksObjectException Thrown if an error occurs while retrieving a BroadWorksObject
     */
    private static ServiceProvider provisionServiceProvider(BroadWorksServer bws, ProvisioningRow provisioningRow) throws RequestException, BroadWorksObjectException {
        String serviceProviderId = provisioningRow.getServiceProviderId();
        ServiceProvider serviceProvider;

        try {
            serviceProvider = ServiceProvider.getPopulatedServiceProvider(bws, serviceProviderId);
        } catch (Exception ex) {
            log.debug("Service Provider does not exist... Creating Service Provider with ID: " + serviceProviderId);

            bws.clearCache();

            ServiceProvider.ServiceProviderAddRequest serviceProviderAddRequest = new ServiceProvider.ServiceProviderAddRequest(bws, serviceProviderId, provisioningRow.getDomain());
            serviceProviderAddRequest.setServiceProviderName(serviceProviderId);
            serviceProviderAddRequest.setFlagIsEnterprise();

            isError(serviceProviderAddRequest.fire());
            serviceProvider = ServiceProvider.getPopulatedServiceProvider(bws, serviceProviderId);
        }

        // Authorize Services
        log.debug("Authorizing ServiceProvider level Services");
        ServiceProviderServiceModifyAuthorizationListRequest authorizationListModifyRequest = new ServiceProviderServiceModifyAuthorizationListRequest(serviceProvider);

        authorizationListModifyRequest.setUserServiceAuthorization(Stream.of(provisioningRow.getServices())
                .flatMap(Collection::stream)
                .map(s -> new UserServiceAuthorization(s).setAuthorizedQuantity(new UnboundedPositiveInt().setFlagUnlimited()))
                .collect(Collectors.toList())
                .toArray(new UserServiceAuthorization[0]));

        isError(authorizationListModifyRequest.fire());

        log.debug("Returning the created SericeProvider");
        return serviceProvider;
    }


    /**
     * Provisions the Group for the ProvisioningRow. If the Group does not exist then it is added.
     * The services for this user are authorized.
     *
     * @param bws             The BroadWorksServer
     * @param serviceProvider The Service Provider that will contain the Group
     * @param provisioningRow The provisioning user
     * @return Returns the created Group
     * @throws RequestException          Thrown if an error occurs while firing a request.
     * @throws BroadWorksObjectException Thrown if an error occurs while retrieving a BroadWorksObject
     */
    private static Group provisionGroup(BroadWorksServer bws, ServiceProvider serviceProvider, ProvisioningRow provisioningRow) throws RequestException, BroadWorksObjectException {
        String groupId = provisioningRow.getGroupId();
        Group group;

        try {
            group = Group.getPopulatedGroup(serviceProvider, groupId);
        } catch (Exception ex) {
            log.debug("Group does not exist... Creating Group with ID: " + groupId);
            // Group Does Not Exist -- Creating
            bws.clearCache();

            GroupAddRequest groupAddRequest = new GroupAddRequest(serviceProvider.getBroadWorksServer(), serviceProvider.getServiceProviderId(), provisioningRow.getGroupId(), provisioningRow.getDomain(), 25);
            groupAddRequest.setGroupName(groupId);
            groupAddRequest.setCallingLineIdName(StringUtils.join(groupId.split("_"), " "));

            isError(groupAddRequest.fire());
            group = Group.getPopulatedGroup(serviceProvider, groupId);
        }

        // Authorize and Assign Services
        log.debug("Authorizing Services and ServicePacks");
        GroupServiceModifyAuthorizationListRequest authorizationListModifyRequest = new GroupServiceModifyAuthorizationListRequest(group);

        authorizationListModifyRequest.setUserServiceAuthorization(Stream.of(provisioningRow.getServices())
                .flatMap(Collection::stream)
                .map(s -> new UserServiceAuthorization(s).setAuthorizedQuantity(new UnboundedPositiveInt().setFlagUnlimited()))
                .collect(Collectors.toList())
                .toArray(new UserServiceAuthorization[0]));

        isError(authorizationListModifyRequest.fire());

        return group;
    }

    /**
     * Provisions the User for the ProvisioningRow.
     *
     * @param bws             The BroadWorksServer
     * @param provisioningRow The provisioning user
     * @return Returns the created User
     * @throws RequestException          Thrown if an error occurs while firing a request.
     * @throws BroadWorksObjectException Thrown if an error occurs while retrieving a BroadWorksObject
     */
    private static User provisionUser(BroadWorksServer bws, ProvisioningRow provisioningRow) throws RequestException, BroadWorksObjectException {
        String userIdWithDomain = provisioningRow.getUserId() + "@" + provisioningRow.getDomain();

        // Create User
        log.info("Creating user with ID: " + provisioningRow.getUserId());
        UserAddRequest userAddRequest = new UserAddRequest(bws,
                provisioningRow.getServiceProviderId(),
                provisioningRow.getGroupId(),
                userIdWithDomain,
                provisioningRow.getUserId(),
                provisioningRow.getUserId(),
                provisioningRow.getUserId(),
                provisioningRow.getUserId()
        );
        userAddRequest.setExtension(provisioningRow.getExtensionDigits());
        userAddRequest.setPassword(provisioningRow.getWebPortalPassword());

        isError(userAddRequest.fire());

        log.debug("Retrieving User");
        User user = User.getPopulatedUser(bws, userIdWithDomain);

        // Assign Services
        UserServiceAssignListRequest userServiceAssignListRequest = new UserServiceAssignListRequest(user);
        userServiceAssignListRequest.setServiceName(provisioningRow.getServices().toArray(new UserService[0]));
        isError(userServiceAssignListRequest.fire());

        // Add Device
        GroupAccessDeviceAddRequest addDeviceRequest = new GroupAccessDeviceAddRequest(bws,
                provisioningRow.getServiceProviderId(),
                provisioningRow.getGroupId(),
                provisioningRow.getIdentityDeviceProfileName(),
                provisioningRow.getIdentityDeviceProfileType());
        isError(addDeviceRequest.fire());

        GroupAccessDevice accessDevice = GroupAccessDevice.getPopulatedGroupAccessDevice(user.getGroup(), provisioningRow.getIdentityDeviceProfileName());

        // Assign Device
        UserModifyRequest assignDeviceRequest = new UserModifyRequest(user);

        AccessDeviceMultipleContactEndpointModify endpoint = new AccessDeviceMultipleContactEndpointModify();
        endpoint.setAccessDevice(accessDevice);
        endpoint.setLinePort(provisioningRow.getLinePort());
        assignDeviceRequest.setEndpoint(new Endpoint().setAccessDeviceEndpoint(endpoint));
        isError(assignDeviceRequest.fire());

        // Set Authentication
        UserAuthentication.UserAuthenticationModifyRequest userAuthenticationModifyRequest = new UserAuthentication.UserAuthenticationModifyRequest(user);
        userAuthenticationModifyRequest.setUserName(provisioningRow.getSipUserName());
        userAuthenticationModifyRequest.setNewPassword(provisioningRow.getSipAuthenticationPassword());
        isError(userAuthenticationModifyRequest.fire());

        return user;
    }

    /**
     * Removes the provisioning user from the BroadWorksPlatform. If the Group is left empty it is deleted. If the ServiceProvider is left empty it is also deleted.
     *
     * @param bws             The BroadWorksServer
     * @param provisioningRow The ProvisioningRow
     * @throws RequestException          Thrown if an error occurs while deprovisioning
     * @throws BroadWorksObjectException Thrown if an error occurs while retrieving BroadWorksObjects
     */
    private static void deprovisionUser(BroadWorksServer bws, ProvisioningRow provisioningRow) throws RequestException, BroadWorksObjectException {
        try {
            log.debug("Retrieving User");
            String userIdWithDomain = provisioningRow.getUserId() + "@" + provisioningRow.getDomain();
            User user = User.getPopulatedUser(bws, userIdWithDomain);

            // Delete User
            UserDeleteRequest userDeleteRequest = new UserDeleteRequest(user);
            isError(userDeleteRequest.fire());

            System.out.println("User removed: " + provisioningRow.getUserId());
        } catch (BroadWorksObjectException ex) {
            System.out.println("User does not exist: " + provisioningRow.getUserId() + " skipping...");
            return;
        }

        bws.clearCache();

        ServiceProvider serviceProvider = ServiceProvider.getPopulatedServiceProvider(bws, provisioningRow.getServiceProviderId());
        Group group = Group.getPopulatedGroup(serviceProvider, provisioningRow.getGroupId());

        if (group.getUserCount() == 0) {
            System.out.println("Group is empty... removing");
            GroupDeleteRequest groupDeleteRequest = new GroupDeleteRequest(group);
            isError(groupDeleteRequest.fire());
        }

        bws.clearCache();

        if (serviceProvider.getGroupsInServiceProvider().size() == 0) {
            System.out.println("ServiceProvider is empty... removing");
            ServiceProviderDeleteRequest serviceProviderDeleteRequest = new ServiceProviderDeleteRequest(serviceProvider);
            isError(serviceProviderDeleteRequest.fire());
        }
    }

    /**
     * Checks if a Response is an error and throws a RequestException if so.
     *
     * @param response The Response to check.
     * @throws RequestException The exception thrown if the Response is an error.
     */
    private static void isError(Response response) throws RequestException {
        if (response.isErrorResponse()) {
            throw new RequestException("Error while performing provisioning: " + response.getSummaryText());
        }
    }

    /**
     * A provisioning User row
     * <p>
     * Fields:
     * enterpriseID,
     * groupID,
     * userID,
     * domain,
     * servicesToAssignCommaDelimited,
     * extensionDigits,
     * webPortalPassword,
     * sipUserName,
     * sipAuthenticationPassword,
     * identityDeviceProfileType,
     * identityDeviceProfileName,
     * linePort
     *
     * @author Matthew Keathley on 3/20/17.
     */
    public static class ProvisioningRow {

        private String serviceProviderId;
        private String groupId;
        private String userId;
        private String domain;
        private List<UserService> services;
        private String extensionDigits;
        private String webPortalPassword;
        private String sipUserName;
        private String sipAuthenticationPassword;
        private String identityDeviceProfileType;
        private String identityDeviceProfileName;
        private String linePort;

        ProvisioningRow(List<String> row) throws NoSuchFieldException {
            this.serviceProviderId = row.get(0);
            this.groupId = row.get(1);
            this.userId = row.get(2);
            this.domain = row.get(3);

            // Parse Services
            String commaSeperatedServices = row.get(4);
            String[] splitServices = commaSeperatedServices.split(",");
            this.services = new ArrayList<>();
            for (String service : splitServices) {
                UserService userService = UserService.get(service);
                if (userService == null)
                    throw new NoSuchFieldException("UserService: " + service + " is not a valid service.");
                services.add(userService);
            }

            this.extensionDigits = row.get(5);
            this.webPortalPassword = row.get(6);
            this.sipUserName = row.get(7);
            this.sipAuthenticationPassword = row.get(8);
            this.identityDeviceProfileType = row.get(9);
            this.identityDeviceProfileName = row.get(10);
            this.linePort = row.get(11);
        }

        String getServiceProviderId() {
            return serviceProviderId;
        }

        String getGroupId() {
            return groupId;
        }

        String getUserId() {
            return userId;
        }

        String getDomain() {
            return domain;
        }

        List<UserService> getServices() {
            return services;
        }

        String getExtensionDigits() {
            return extensionDigits;
        }

        String getWebPortalPassword() {
            return webPortalPassword;
        }

        String getSipUserName() {
            return sipUserName;
        }

        String getSipAuthenticationPassword() {
            return sipAuthenticationPassword;
        }

        String getIdentityDeviceProfileType() {
            return identityDeviceProfileType;
        }

        String getIdentityDeviceProfileName() {
            return identityDeviceProfileName;
        }

        String getLinePort() {
            return linePort;
        }
    }

}
