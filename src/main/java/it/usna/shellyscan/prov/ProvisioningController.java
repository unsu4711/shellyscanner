package it.usna.shellyscan.prov;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.Optional;
import java.util.HashMap;

import it.usna.shellyscan.model.Devices;
import it.usna.shellyscan.model.device.ShellyAbstractDevice;

public class ProvisioningController {

    private final DeviceStore deviceStore;
    private final NetshWifiManager wifiManager;
    private final ShellyApiClient apiClient;
    private final ProvisioningConfig config;
    private final Devices model;
    private final Consumer<String> logger;

    public ProvisioningController(Devices model, Consumer<String> logger) {
        this.model = model;
        this.logger = logger;
        this.deviceStore = new DeviceStore();
        this.wifiManager = new NetshWifiManager(logger);
        this.apiClient = new ShellyApiClient();
        this.config = ProvisioningConfig.load();
    }

    public void discoverShellyAPs() {
        if (!ensureHomeWifiConnection()) return;
        log("--- Starting Shelly AP Discovery Pass ---");
        
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();
        log("Loaded " + devices.size() + " known devices.");
        
        List<String> visibleAps = wifiManager.scanForShellyAPs();
        if (visibleAps.isEmpty()) {
            log("No Shelly APs found in the current scan.");
            return;
        }
        
        int newDevicesFound = 0;
        String now = java.time.Instant.now().toString();
        
        for (String ssid : visibleAps) {
            if (!devices.containsKey(ssid)) {
                ProvisionedShellyDevice newDevice = new ProvisionedShellyDevice(
                    null, "discovered", now, now, null, null, null, null, null, null, null
                );
                devices.put(ssid, newDevice);
                newDevicesFound++;
                log("Discovered new device: " + ssid);
            }
        }
        
        if (newDevicesFound > 0) {
            deviceStore.saveDiscoveredDevices(devices);
            log("Saved updated device list.");
        } else {
            log("No new Shelly APs discovered.");
        }
        log("--- AP Discovery Pass Complete ---");
    }

    public void discoverProvisionedDevicesOnLAN() {
        if (!ensureHomeWifiConnection()) return;
        log("--- Scanning for provisioned Shelly devices on the local network (rescan) ---");
        try {
            model.rescan(true);
        } catch (Exception e) {
            log("ERROR during rescan: " + e.getMessage());
        }
        log("--- Rescan Complete ---");
    }

    public void startContinuousDiscovery() {
        log("Entering continuous AP Discovery Mode. Press 'Stop' to exit.");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                discoverShellyAPs();
                log("Scan complete. Waiting " + config.getScanLoopDelaySeconds() + " seconds before next scan...");
                Thread.sleep(config.getScanLoopDelaySeconds() * 1000);
            }
        } catch (InterruptedException e) {
            log("Continuous discovery stopped.");
            Thread.currentThread().interrupt(); // Preserve the interrupted status
        }
    }

    public void connectDevices() {
        log("--- Starting Shelly Connection Pass ---");
        model.stopAllRefreshes();
        model.pauseMdns();
        try {
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
                    devices.put(shellySsid, updateStatus(devices.get(shellySsid), "failed"));
                    continue;
                }

                if (!apiClient.waitForShellyApReachable(60)) {
                    log("ERROR: Shelly AP was not reachable after connecting.");
                    devices.put(shellySsid, updateStatus(devices.get(shellySsid), "failed"));
                    continue;
                }

                if (apiClient.configureShellyWifi(config.getHomeWifiSsid(), config.getHomeWifiPassword())) {
                     log("Successfully configured " + shellySsid);
                     devices.put(shellySsid, updateStatus(devices.get(shellySsid), "connected"));
                } else {
                    log("ERROR: Failed to configure " + shellySsid);
                    devices.put(shellySsid, updateStatus(devices.get(shellySsid), "failed"));
                }
            }

            deviceStore.saveDiscoveredDevices(devices);
            log("Device statuses saved to shelly_devices.json");

            log("Attempting to reconnect to home Wi-Fi...");
            wifiManager.connect(config.getHomeWifiSsid(), config.getHomeWifiPassword());
            log("--- Connection Pass Complete ---");
        } finally {
            model.startAllRefreshes();
            model.resumeMdns();
            try { model.rescan(true); } catch (Exception ignore) {}
        }
    }

    public void setupDevices() {
        if (!ensureHomeWifiConnection()) return;
        log("--- Starting Shelly Device Setup on Home Network ---");
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();

        // Rescan to update the main model with current devices
        discoverProvisionedDevicesOnLAN();

        // Sequential name counters by type prefix
        Map<String, Integer> counters = new HashMap<>();

        int renamed = 0;
        for (Map.Entry<String, ProvisionedShellyDevice> entry : devices.entrySet()) {
            String ssid = entry.getKey();
            ProvisionedShellyDevice info = entry.getValue();
            if (!"connected".equals(info.status())) continue;

            String ip = findIpForSsid(ssid);
            if (ip == null) {
                log("Could not find device on LAN for SSID: " + ssid + ". Skipping.");
                continue;
            }

            ShellyApiClient.ShellyDeviceInfo devInfo = apiClient.getShellyDeviceInfo(ip);
            if (devInfo == null) {
                log("Failed to get device info from " + ip + ". Skipping.");
                continue;
            }

            int gen = 2;
            try { if (devInfo.gen() != null) gen = Integer.parseInt(devInfo.gen()); } catch (Exception ignore) {}
            String typePrefix = deriveTypePrefix(ssid, devInfo.model());
            String newName = generateName(typePrefix, counters);

            if (apiClient.setShellyDeviceName(ip, gen, newName)) {
                log("Renamed " + ssid + " to " + newName);
                ProvisionedShellyDevice updated = new ProvisionedShellyDevice(
                    info.bssid(), "setup", info.firstSeen(), info.lastSeen(), null,
                    devInfo.mac(), devInfo.model(), ip, devInfo.mac(), devInfo.fw_id(), newName
                );
                devices.put(ssid, updated);
                renamed++;
            } else {
                log("Failed to set name for " + ssid + ".");
            }
        }

        if (renamed > 0) {
            deviceStore.saveDiscoveredDevices(devices);
            log("Saved updated names for " + renamed + " device(s).");
        }
        log("--- Setup Pass Complete ---");
    }

    public void listDevices() {
        if (!ensureHomeWifiConnection()) return;
        log("--- Discovered Shelly Devices ---");
        Map<String, ProvisionedShellyDevice> devices = deviceStore.loadDiscoveredDevices();
        if (devices.isEmpty()) {
            log("No devices discovered yet.");
            return;
        }
        
        // Simple list format for the log
        devices.forEach((ssid, device) -> {
            log(String.format("SSID: %s, Status: %s, IP: %s, Type: %s, Name: %s",
                ssid,
                device.status(),
                device.homeIpAddress() != null ? device.homeIpAddress() : "N/A",
                device.deviceType() != null ? device.deviceType() : "N/A",
                device.name() != null ? device.name() : "N/A"
            ));
        });
        log("--- End of Device List ---");
    }

    private boolean ensureHomeWifiConnection() {
        log("Checking current Wi-Fi connection...");
        Optional<String> currentSsidOpt = wifiManager.getCurrentSsid();

        if (currentSsidOpt.isPresent()) {
            String currentSsid = currentSsidOpt.get();
            log("Currently connected to Wi-Fi: " + currentSsid);
            if (currentSsid.equals(config.getHomeWifiSsid())) {
                log("Already connected to the correct home Wi-Fi.");
                return true;
            } else {
                log("WARNING: Not connected to the configured home Wi-Fi (" + config.getHomeWifiSsid() + ").");
            }
        } else {
            log("Not currently connected to any Wi-Fi network.");
        }

        log("Attempting to connect to home Wi-Fi: " + config.getHomeWifiSsid());
        if (wifiManager.connect(config.getHomeWifiSsid(), config.getHomeWifiPassword())) {
            log("Successfully reconnected to home Wi-Fi.");
            return true;
        } else {
            log("ERROR: Failed to reconnect to home Wi-Fi. Cannot proceed.");
            return false;
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    private ProvisionedShellyDevice updateStatus(ProvisionedShellyDevice prev, String status) {
        if (prev == null) {
            String now = java.time.Instant.now().toString();
            return new ProvisionedShellyDevice(null, status, now, now, null, null, null, null, null, null, null);
        }
        return prev.withStatus(status);
    }

    private String findIpForSsid(String ssid) {
        String base = ssid.toLowerCase().split("-", 2)[0];
        for (int i = 0; i < model.size(); i++) {
            ShellyAbstractDevice d = model.get(i);
            String host = d.getHostname() != null ? d.getHostname().toLowerCase() : "";
            if (host.contains(base)) {
                return d.getAddressAndPort().getAddress().getHostAddress();
            }
        }
        return null;
    }

    private String deriveTypePrefix(String ssid, String model) {
        if (ssid != null && ssid.contains("-")) {
            return ssid.split("-", 2)[0].toLowerCase();
        }
        if (model != null && !model.isEmpty()) {
            return model.toLowerCase();
        }
        return "shelly";
    }

    private String generateName(String typePrefix, Map<String,Integer> counters) {
        int n = counters.getOrDefault(typePrefix, 0) + 1;
        counters.put(typePrefix, n);
        return typePrefix + "_jc_" + n;
    }
}
