import co.ecg.alpaca.toolkit.exception.BroadWorksServerException;
import co.ecg.alpaca.toolkit.exception.HelperException;
import co.ecg.alpaca.toolkit.exception.RequestException;
import co.ecg.alpaca.toolkit.generated.*;
import co.ecg.alpaca.toolkit.generated.SystemAccessDevice.SystemAccessDeviceGetAllRequest;
import co.ecg.alpaca.toolkit.generated.SystemAccessDevice.SystemAccessDeviceGetAllResponse;
import co.ecg.alpaca.toolkit.generated.enums.AccessDeviceLevel;
import co.ecg.alpaca.toolkit.generated.tables.SystemAccessDeviceAccessDeviceTableRow;
import co.ecg.alpaca.toolkit.helper.user.UserHelper;
import co.ecg.alpaca.toolkit.messaging.request.RequestHelper;
import co.ecg.alpaca.toolkit.model.BroadWorksServer;
import co.ecg.utilities.properties.P;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PhoneRebootUtility {

        private static final String BLACKLIST = "blacklist";
        private static final String USER_AGENT = "useragent";
        private static final String DRYRUN = "dryrun";
        public static Logger log = LogManager.getLogger(PhoneRebootUtility.class);
        public static String blacklist = null;
        public static String useragent = null;
        public static List<String> excludeList = new ArrayList<String>();
        private static boolean dryrun = true;

        public static void main(String[] args)
            throws BroadWorksServerException, IOException, InterruptedException, HelperException, RequestException {

                CommandLineParser parser = new PosixParser();
                Options options = new Options();

                options.addOption(BLACKLIST, true, "Blacklist");
                options.addOption(USER_AGENT, true, "UserAgent REGEX");
                options.addOption(DRYRUN, true, "Dryrun");

                CommandLine cmd = null;

                try {

                        cmd = parser.parse(options, args);

                        if (cmd.hasOption(DRYRUN)) {
                                dryrun = Boolean.parseBoolean(cmd.getOptionValue(DRYRUN));
                        } else {
                                usage("Missing Required Parameter - " + DRYRUN);
                        }

                        if (cmd.hasOption(USER_AGENT)) {
                                useragent = cmd.getOptionValue(USER_AGENT);
                        } else {
                                usage("Missing Required Parameter - " + USER_AGENT);
                        }

                        if (cmd.hasOption(BLACKLIST)) {
                                blacklist = cmd.getOptionValue(BLACKLIST);

                                BufferedReader br = null;
                                try {
                                        br = new BufferedReader(new FileReader(blacklist));

                                        for (String line; (line = br.readLine()) != null; ) {
                                                if ((line.length() == 12) && (!excludeList.contains(line))) {
                                                        excludeList.add(line.toLowerCase());
                                                }
                                        }

                                } catch (FileNotFoundException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                } catch (IOException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                }

                        }

                        if (cmd.hasOption(USER_AGENT)) {

                        }

                } catch (ParseException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                        System.exit(1);
                }

                BroadWorksServer bws = BroadWorksServer.getBroadWorksServer(P.getProperties().getPrimaryBroadWorksServer());

                System.out.println("Dryrun: " + dryrun);

                ResetThread resetThread = new ResetThread(dryrun);
                Thread t = new Thread(resetThread);
                t.start();

                System.out.print("Retrieving a list of all users in the system");
                List<User> userList = UserHelper.getAllUsersInSystem(bws);
                System.out.println(" -- complete");

                ConcurrentHashMap<String, User> userMap = new ConcurrentHashMap<>();

                System.out.print("Populating list of users");

                RequestHelper.requestPerObjectProducer(bws,
                    userList,
                    User.class,
                    User.UserGetRequest.class,
                    (u, response) -> {
                            u.populate(response);
                            userMap.put(u.getUserId(), u);
                    });

                System.out.println(" -- complete");

                System.out.print("Retrieving a list of all registrations");

                HashMap<User, User.UserGetRegistrationListResponse>
                    registrationResponseMap =
                    UserHelper.getResponsePerUserMap(bws, userList, User.UserGetRegistrationListRequest.class);

                System.out.println(" -- complete");

                Pattern pattern = Pattern.compile(useragent);

                try {

                        System.out.print("Retrieving a list of all devices");

                        SystemAccessDeviceGetAllRequest request = new SystemAccessDeviceGetAllRequest(bws);
                        SystemAccessDeviceGetAllResponse response = request.fire();

                        if (response.isErrorResponse()) {
                                throw new BroadWorksServerException(response.getSummaryText());
                        }

                        System.out.println(" -- complete");

                        List<SystemAccessDeviceAccessDeviceTableRow>
                            devices =
                            response.getAccessDeviceTable().stream().filter(d -> {
                                    return !(d.getGroupId().isEmpty() || d.getServiceProviderId().isEmpty());
                            }).collect(Collectors.toList());

                        log.trace("Query returned - " + devices.size() + " results.");

                        int count = 0;

                        System.out.print("Retrieving a list of all group access devices");

                        List<GroupAccessDevice> gadList = new ArrayList<>();

                        for (SystemAccessDeviceAccessDeviceTableRow device : devices) {
                                ServiceProvider
                                    serviceProvider =
                                    new ServiceProvider(bws, device.getServiceProviderId());
                                Group group = new Group(serviceProvider, device.getGroupId());
                                GroupAccessDevice gad = new GroupAccessDevice(group, device.getDeviceName());
                                gadList.add(gad);
                        }

                        HashMap<ImmutableTriple<String, String, String>, GroupAccessDevice> gadMap = new HashMap<>();

                        RequestHelper.requestPerObjectProducer(bws,
                            gadList,
                            GroupAccessDevice.class,
                            GroupAccessDevice.GroupAccessDeviceGetRequest.class,
                            (gad, gadResponse) -> {
                                    gad.populate(gadResponse);
                                    ImmutableTriple<String, String, String>
                                        key =
                                        new ImmutableTriple<>(gad.getServiceProviderId(),
                                            gad.getGroupId(),
                                            gad.getDeviceName());
                                    gadMap.put(key, gad);
                            });

                        System.out.println(" -- complete");

                        List<GroupAccessDevice>
                            gadResetList =
                            Collections.synchronizedList(new ArrayList<GroupAccessDevice>());

                        registrationResponseMap.values().stream().parallel().forEach(reg -> {
                                reg.getRegistrationTable().stream().forEach(row -> {
                                        String userAgent = row.getUserAgent();

                                        Matcher matcher = pattern.matcher(userAgent);

                                        if (!matcher.find()) {

                                                if (row.getDeviceLevel().equals(AccessDeviceLevel.GROUP.value())) {

                                                        AccessDevice device = row.getAccessDevice(bws);

                                                        User u = userMap.get(reg.getUser().getUserId());

                                                        GroupAccessDevice
                                                            gad =
                                                            gadMap.get(new ImmutableTriple<>(u.getServiceProviderId(),
                                                                u.getGroupId(),
                                                                row.getDeviceName()));

                                                        if (gad != null) {

                                                                if (gad.getDeviceType().startsWith("Poly")) {
                                                                        gadResetList.add(gad);
                                                                }
                                                        }
                                                }

                                        } else {
                                                System.out.println(
                                                    "SKIP - " + row.getDeviceName() + " - " + row.getUserAgent());
                                        }

                                });
                        });

                        gadResetList.stream().distinct().forEach(g -> {
                                try {

                                        if (!excludeList.contains(g.getMacAddress().toLowerCase())) {
                                                resetThread.addResetRequest(new GroupAccessDevice.GroupAccessDeviceResetRequest(
                                                    g));
                                        } else {
                                                System.out.println("EXCLUDE - " + g.getMacAddress());
                                        }

                                } catch (InterruptedException e) {
                                        e.printStackTrace();
                                }

                        });

                } catch (RequestException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                }

                System.out.println("Waiting for the reset queue to empty");

                while (!resetThread.isEmpty()) {
                        Thread.sleep(1000);
                }

                System.out.println("The reset queue is now empty");

                System.exit(0);

        }

        public static void usage(String message) {
                System.out.print(
                    "Usage: PhoneRebootUtility --dryrun <boolean> --useragent <regex> [--blacklist <blacklist>]");
                System.out.print("\n" + message + "\n");
                System.exit(1);
        }

        public static class ResetThread implements Runnable {

                private final LinkedBlockingQueue<GroupAccessDevice.GroupAccessDeviceResetRequest>
                    resetQueue =
                    new LinkedBlockingQueue();

                private boolean dryrun = true;

                public ResetThread(boolean dryrun) {
                        this.dryrun = dryrun;
                }

                public void addResetRequest(GroupAccessDevice.GroupAccessDeviceResetRequest request)
                    throws InterruptedException {
                        resetQueue.put(request);
                }

                public boolean isEmpty() {
                        return resetQueue.isEmpty();
                }

                @Override
                public void run() {

                        while (true) {
                                try {
                                        GroupAccessDevice.GroupAccessDeviceResetRequest request = resetQueue.take();

                                        GroupAccessDevice gad = request.getGroupAccessDevice();

                                        System.out.println("Reset - " + gad.getDeviceName() + " - " + gad.getVersion());

                                        if (!dryrun) {
                                                request.asyncFire(response -> {

                                                        if (response.isErrorResponse()) {
                                                                System.out.println(response.getDetailText());
                                                        }

                                                });

                                                Thread.sleep(300);
                                        }


                                } catch (InterruptedException | RequestException e) {
                                        e.printStackTrace();
                                }
                        }

                }
        }

}
