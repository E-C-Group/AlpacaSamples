import co.ecg.alpaca.toolkit.exception.BroadWorksObjectException;
import co.ecg.alpaca.toolkit.exception.BroadWorksServerException;
import co.ecg.alpaca.toolkit.exception.HelperException;
import co.ecg.alpaca.toolkit.exception.RequestException;
import co.ecg.alpaca.toolkit.generated.Group;
import co.ecg.alpaca.toolkit.generated.GroupAccessDevice;
import co.ecg.alpaca.toolkit.generated.ServiceProvider;
import co.ecg.alpaca.toolkit.generated.SystemAccessDevice.SystemAccessDeviceGetAllRequest;
import co.ecg.alpaca.toolkit.generated.SystemAccessDevice.SystemAccessDeviceGetAllResponse;
import co.ecg.alpaca.toolkit.generated.User;
import co.ecg.alpaca.toolkit.generated.User.UserGetRegistrationListRequest;
import co.ecg.alpaca.toolkit.generated.User.UserGetRegistrationListResponse;
import co.ecg.alpaca.toolkit.generated.enums.AccessDeviceLevel;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A tool to Reboot all Group Access Devices within a BroadWorks System.
 */
public class PhoneRebootUtility {

    private static final String BLACKLIST = "blackList";
    private static final String USER_AGENT = "userAgent";
    private static final String DRYRUN = "dryRun";
    public static Logger log = LogManager.getLogger(PhoneRebootUtility.class);
    private static String userAgent;
    private static Set<String> excludeList = new HashSet<>();
    private static boolean dryRun = true;

    public static void main(String[] args) throws BroadWorksServerException, IOException, InterruptedException, HelperException, RequestException {

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        options.addOption(BLACKLIST, true, "Blacklist");
        options.addOption(USER_AGENT, true, "UserAgent REGEX");
        options.addOption(DRYRUN, true, "Dryrun");

        try {
            CommandLine commandLine = parser.parse(options, args);

            // Parse Command Line Options
            if (commandLine.hasOption(DRYRUN)) {
                dryRun = Boolean.parseBoolean(commandLine.getOptionValue(DRYRUN));
            } else {
                usage("Missing Required Parameter - " + DRYRUN);
            }

            if (commandLine.hasOption(USER_AGENT)) {
                userAgent = commandLine.getOptionValue(USER_AGENT);
            } else {
                usage("Missing Required Parameter - " + USER_AGENT);
            }

            if (commandLine.hasOption(BLACKLIST)) {
                String blackList = commandLine.getOptionValue(BLACKLIST);

                // Parse BlackList
                try {
                    BufferedReader bufferedReader = new BufferedReader(new FileReader(blackList));
                    String line;

                    while ((line = bufferedReader.readLine()) != null) {
                        if (line.length() == 12) excludeList.add(line.toLowerCase());
                    }
                } catch (IOException ex) {
                    System.err.println("An error occurred while parsing the BlackList - " + ex.getMessage());
                }

            }
        } catch (ParseException ex) {
            System.err.println("An error occurred while parsing the Command Line - " + ex.getMessage());
            System.exit(1);
        }

        // Open the Connection to BroadWorks
        BroadWorksServer broadWorksServer = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

        System.out.println("Dryrun: " + dryRun);

        // Create reset thread
        ResetThread resetThread = new ResetThread(dryRun);
        Thread thread = new Thread(resetThread);
        thread.start();

        // Retrieve all Users in System.
        System.out.print("Retrieving a list of all users in the system.");
        List<User> userList = UserHelper.getAllUsersInSystem(broadWorksServer);
        System.out.println(" -- complete");


        // UserId -> User Map
        ConcurrentHashMap<String, User> userMap = new ConcurrentHashMap<>();

        // Populate User Map
        System.out.print("Populating list of users.");
        RequestHelper.requestPerObjectProducer(broadWorksServer,
                userList,
                User.class,
                User.UserGetRequest.class,
                (user, response) -> {
                    // Populate User
                    user.populate(response);

                    // Add User to Map
                    userMap.put(user.getUserId(), user);
                });

        System.out.println(" -- complete");
        System.out.print("Retrieving a list of all registrations");

        // User -> User Registration List Map
        HashMap<User, UserGetRegistrationListResponse> registrationResponseMap =
                UserHelper.getResponsePerUserMap(broadWorksServer, userList, UserGetRegistrationListRequest.class);
        System.out.println(" -- complete");

        // User Agent Regex
        Pattern pattern = Pattern.compile(userAgent);

        System.out.print("Retrieving a list of all Group Access Devices");

        // Retrieve All Devices in System.
        SystemAccessDeviceGetAllResponse systemAccessDeviceGetAllResponse = new SystemAccessDeviceGetAllRequest(broadWorksServer).fire();

        if (systemAccessDeviceGetAllResponse.isErrorResponse()) {
            System.err.println("Error firing request - " + systemAccessDeviceGetAllResponse.getDetailText());
            System.exit(1);
        }

        // Retrieve all Group Access Devices
        List<GroupAccessDevice> groupAccessDeviceList = systemAccessDeviceGetAllResponse
                .getAccessDeviceTable()
                .stream()
                .filter(device -> device.getGroupId() != null && !device.getGroupId().isEmpty())
                .map(device -> {
                    ServiceProvider serviceProvider = new ServiceProvider(broadWorksServer, device.getServiceProviderId());
                    Group group = new Group(serviceProvider, device.getGroupId());
                    GroupAccessDevice groupAccessDevice = null;

                    try {
                        groupAccessDevice = GroupAccessDevice.getPopulatedGroupAccessDevice(group, device.getDeviceName());
                    } catch (BroadWorksObjectException ex) {
                        System.err.println("Error while populating GroupAccessDevice list - " + ex.getMessage());
                    }

                    return groupAccessDevice;
                })
                .collect(Collectors.toList());

        System.out.println(" -- complete");

        // Triple(ServiceProvider ID, Group ID, Device Name) -> GroupAccessDevice Map
        HashMap<ImmutableTriple<String, String, String>, GroupAccessDevice> groupAccessDeviceMap = new HashMap<>();

        RequestHelper.requestPerObjectProducer(broadWorksServer,
                groupAccessDeviceList,
                GroupAccessDevice.class,
                GroupAccessDevice.GroupAccessDeviceGetRequest.class,
                (groupAccessDevice, gadResponse) -> {
                    ImmutableTriple<String, String, String> key = new ImmutableTriple<>(groupAccessDevice.getServiceProviderId(), groupAccessDevice.getGroupId(), groupAccessDevice.getDeviceName());
                    groupAccessDeviceMap.put(key, groupAccessDevice);
                });

        System.out.println(" -- complete");

        List<GroupAccessDevice> deviceResetList = Collections.synchronizedList(new ArrayList<GroupAccessDevice>());

        // Loop through registrations
        registrationResponseMap.values().stream().parallel().forEach(registration -> {
            registration.getRegistrationTable().forEach(row -> {
                String userAgent = row.getUserAgent();

                Matcher matcher = pattern.matcher(userAgent);

                if (!matcher.find()) {
                    if (row.getDeviceLevel().equals(AccessDeviceLevel.GROUP.value())) {
                        User user = userMap.get(registration.getBroadWorksUser().getUserId());
                        GroupAccessDevice device = groupAccessDeviceMap.get(new ImmutableTriple<>(user.getServiceProviderId(), user.getGroupId(), row.getDeviceName()));
                        if (device != null && device.getDeviceType().startsWith("Poly")) deviceResetList.add(device);
                    }
                } else {
                    System.out.println("SKIP - " + row.getDeviceName() + " - " + row.getUserAgent());
                }
            });
        });

        // Loop through devices and reset.
        deviceResetList.stream().distinct().forEach(device -> {
            try {
                if (!excludeList.contains(device.getMacAddress().toLowerCase())) {
                    resetThread.addResetRequest(new GroupAccessDevice.GroupAccessDeviceResetRequest(device));
                } else {
                    System.out.println("EXCLUDE - " + device.getMacAddress());
                }
            } catch (InterruptedException ex) {
                System.err.println("Error occurred while resetting device - " + ex.getMessage());
            }
        });

        System.out.println("Waiting for the reset queue to empty");
        while (!resetThread.isEmpty()) {
            Thread.sleep(1000);
        }

        System.out.println("The reset queue is now empty");
        System.exit(0);
    }

    /**
     * Prints the Usage Statement for the tool.
     *
     * @param message The message to print out.
     */
    public static void usage(String message) {
        System.out.print("Usage: PhoneRebootUtility --dryRun <boolean> --userAgent <regex> [--blackList <blackList>]");
        System.out.print("\n" + message + "\n");
        System.exit(1);
    }

    /**
     * The ResetThread Class
     */
    public static class ResetThread implements Runnable {

        private final LinkedBlockingQueue<GroupAccessDevice.GroupAccessDeviceResetRequest> resetQueue = new LinkedBlockingQueue();

        private boolean dryrun = true;

        ResetThread(boolean dryrun) {
            this.dryrun = dryrun;
        }

        void addResetRequest(GroupAccessDevice.GroupAccessDeviceResetRequest request)
                throws InterruptedException {
            resetQueue.put(request);
        }

        boolean isEmpty() {
            return resetQueue.isEmpty();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    GroupAccessDevice.GroupAccessDeviceResetRequest request = resetQueue.take();
                    GroupAccessDevice groupAccessDevice = request.getGroupAccessDevice();
                    System.out.println("Reset - " + groupAccessDevice.getDeviceName() + " - " + groupAccessDevice.getVersion());

                    if (!dryrun) {
                        request.asyncFire(response -> {
                            if (response.isErrorResponse()) {
                                System.out.println(response.getDetailText());
                            }
                        });
                        Thread.sleep(300);
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

        }
    }

}