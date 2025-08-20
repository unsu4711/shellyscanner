package it.usna.shellyscan.prov;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ProvisioningController {

    private final DeviceStore deviceStore;
    private final NetshWifiManager wifiManager;
    private final ShellyApiClient apiClient;
    private final ShellyMdnsDiscoverer mdnsDiscoverer;
    private final ProvisioningConfig config;
    private final Consumer<String> logger;

    public ProvisioningController(Consumer<String> logger) {
        this.logger = logger;
        this.deviceStore = new DeviceStore();
        this.wifiManager = new NetshWifiManager();
        this.apiClient = new ShellyApiClient();
        this.mdnsDiscoverer = new ShellyMdnsDiscoverer();
        this.config = ProvisioningConfig.load();
    }

    public void discoverDevices() {
        log("Starting Shelly Discovery Pass...");
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();
        log("Loaded " + devices.size() + " known devices.");

        List<String> visibleAps = wifiManager.scanForShellyAPs();
        if (visibleAps.isEmpty()) {
            log("No Shelly APs found in the current scan.");
            return;
        }

        int newDevicesFound = 0;
        String now = Instant.now().toString();

        for (String ssid : visibleAps) {
            if (!devices.containsKey(ssid)) {
                ProvisionedShellyDevice newDevice = new ProvisionedShellyDevice(
                    null, "discovered", now, now, null, null, null, null, null, null, null
                );
                devices.put(ssid, newDevice);
                newDevicesFound++;
                log("Discovered new device: " + ssid);
            } else {
                // In a real implementation, you might update the 'last_seen' timestamp here.
            }
        }

        if (newDevicesFound > 0) {
            log("Found " + newDevicesFound + " new Shelly APs. Saving updated device list.");
            deviceStore.saveDiscoveredDevices(devices);
        } else {
            log("No new Shelly APs discovered.");
        }
        log("--- Discovery Pass Complete ---");
    }

    public void connectDevices() {
        log("--- Starting Shelly Connection Pass ---");
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();
        
        List<String> devicesToProvision = devices.entrySet().stream()
            .filter(entry -> "discovered".equals(entry.getValue().status()) || "failed".equals(entry.getValue().status()))
            .map(Map.Entry::getKey)
            .toList();

        if (devicesToProvision.isEmpty()) {
            log("No discovered or failed devices to connect.");
            return;
        }
        log("Found " + devicesToProvision.size() + " devices pending connection.");

        for (String shellySsid : devicesToProvision) {
            log("--- Connecting Device: " + shellySsid + " ---");
            wifiManager.disconnect();
            
            if (!wifiManager.connect(shellySsid, null)) {
                log("ERROR: Failed to connect to Shelly AP: " + shellySsid);
                // Update device status to "failed"
                continue;
            }

            if (apiClient.configureShellyWifi(config.getHomeWifiSsid(), config.getHomeWifiPassword())) {
                 log("Successfully configured " + shellySsid);
                 // Update device status to "connected"
            } else {
                log("ERROR: Failed to configure " + shellySsid);
                // Update device status to "failed"
            }
        }

        log("Attempting to reconnect to home Wi-Fi...");
        wifiManager.connect(config.getHomeWifiSsid(), config.getHomeWifiPassword());
        log("--- Connection Pass Complete ---");
    }

    public void setupDevices() {
        log("--- Starting Shelly Device Setup on Home Network ---");
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();
        Map<String, String> mdnsDiscoveredDevices = mdnsDiscoverer.discover(10);

        List<Map.Entry<String, ProvisionedShellyDevice>> devicesToSetup = devices.entrySet().stream()
            .filter(entry -> "connected".equals(entry.getValue().status()))
            .toList();

        for (Map.Entry<String, ProvisionedShellyDevice> entry : devicesToSetup) {
            String ssid = entry.getKey();
            log("--- Setting up Device: " + ssid + " ---");
            
            String ipAddress = mdnsDiscoveredDevices.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains(ssid.toLowerCase().split("-")[0]))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

            if (ipAddress == null) {
                log("Could not find device " + ssid + " on the home network via mDNS.");
                continue;
            }

            ShellyApiClient.ShellyDeviceInfo info = apiClient.getShellyDeviceInfo(ipAddress);
            if (info != null) {
                log("Found device info: " + info);
                // Persist device info and update status to "setup"
            } else {
                 log("Failed to get device info from " + ipAddress);
            }
        }
        log("--- Setup Pass Complete ---");
    }

    public void listDevices() {
        log("--- Discovered Shelly Devices ---");
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();
        if (devices.isEmpty()) {
            log("No devices discovered yet.");
            return;
        }
        
        // Simple list format for the log
        devices.forEach((ssid, device) -> {
            log(String.format("SSID: %s, Status: %s, IP: %s, Type: %s",
                ssid,
                device.status(),
                device.homeIpAddress() != null ? device.homeIpAddress() : "N/A",
                device.deviceType() != null ? device.deviceType() : "N/A"
            ));
        });
        log("--- End of Device List ---");
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
