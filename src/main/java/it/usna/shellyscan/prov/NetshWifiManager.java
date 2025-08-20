package it.usna.shellyscan.prov;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class NetshWifiManager {

    private static final String SHELLY_AP_SSID_REGEX = "^Shelly.*-\\w*$";
    private final Pattern shellyApPattern = Pattern.compile(SHELLY_AP_SSID_REGEX, Pattern.CASE_INSENSITIVE);
    private final Pattern ssidPattern = Pattern.compile("^SSID \\d+ : (.+)$");
    private final Pattern currentSsidPattern = Pattern.compile("^\\s*SSID\\s*: (.+)$");
    private final Consumer<String> logger;

    public NetshWifiManager(Consumer<String> logger) {
        this.logger = logger;
    }

    public Optional<String> getCurrentSsid() {
        try {
            log("Executing: netsh wlan show interfaces");
            String commandOutput = runCommand("netsh", "wlan", "show", "interfaces");
            for (String line : commandOutput.split("\\r?\\n")) {
                Matcher matcher = currentSsidPattern.matcher(line);
                if (matcher.matches()) {
                    return Optional.of(matcher.group(1).trim());
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<String> scanForShellyAPs() {
        List<String> foundShellyAPs = new ArrayList<>();
        try {
            log("Executing: netsh wlan show networks");
            String commandOutput = runCommand("netsh", "wlan", "show", "networks");
            
            for (String line : commandOutput.split("\\r?\\n")) {
                Matcher ssidMatcher = ssidPattern.matcher(line.trim());
                if (ssidMatcher.matches()) {
                    String ssid = ssidMatcher.group(1);
                    if (shellyApPattern.matcher(ssid).matches()) {
                        foundShellyAPs.add(ssid);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(); // Or proper logging
        }
        return foundShellyAPs;
    }

    public boolean connect(String ssid, String password) {
        try {
            log("Generating Wi-Fi profile for: " + ssid);
            String profileXml = generateWifiProfileXml(ssid, password);
            Path profilePath = Files.createTempFile(ssid + "_profile", ".xml");
            Files.writeString(profilePath, profileXml);

            // Using "name=" and "filename=" with quotes to handle SSIDs with spaces
            log("Executing: netsh wlan delete profile name=\"" + ssid + "\"");
            runCommand("netsh", "wlan", "delete", "profile", "name=\"" + ssid + "\"");
            Thread.sleep(1000); // Small delay for profile deletion
            
            log("Executing: netsh wlan add profile filename=\"" + profilePath.toAbsolutePath().toString() + "\"");
            runCommand("netsh", "wlan", "add", "profile", "filename=\"" + profilePath.toAbsolutePath().toString() + "\"");
            Thread.sleep(1000); // Small delay for profile addition

            log("Executing: netsh wlan connect name=\"" + ssid + "\"");
            runCommand("netsh", "wlan", "connect", "name=\"" + ssid + "\"");
            log("Waiting for connection to establish...");
            Thread.sleep(10000); // Wait for connection to establish

            Files.delete(profilePath);
            log("Successfully connected to " + ssid);
            return true;
        } catch (IOException | InterruptedException e) {
            log("ERROR connecting to " + ssid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean disconnect() {
        try {
            log("Executing: netsh wlan disconnect");
            runCommand("netsh", "wlan", "disconnect");
            return true;
        } catch (IOException | InterruptedException e) {
            log("ERROR disconnecting: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String generateWifiProfileXml(String ssid, String password) {
        String ssidHex = ssid.chars().mapToObj(c -> String.format("%02x", c)).reduce("", String::concat);
        
        if (password != null && !password.isEmpty()) {
            // WPA2 Profile
            return "<?xml version=\"1.0\"?>\n" +
                   "<WLANProfile xmlns=\"http://www.microsoft.com/networking/WLAN/profile/v1\">\n" +
                   "    <name>" + ssid + "</name>\n" +
                   "    <SSIDConfig>\n" +
                   "        <SSID>\n" +
                   "            <hex>" + ssidHex + "</hex>\n" +
                   "            <name>" + ssid + "</name>\n" +
                   "        </SSID>\n" +
                   "    </SSIDConfig>\n" +
                   "    <connectionType>ESS</connectionType>\n" +
                   "    <connectionMode>auto</connectionMode>\n" +
                   "    <MSM>\n" +
                   "        <security>\n" +
                   "            <authEncryption>\n" +
                   "                <authentication>WPA2PSK</authentication>\n" +
                   "                <encryption>AES</encryption>\n" +
                   "                <useOneX>false</useOneX>\n" +
                   "            </authEncryption>\n" +
                   "            <sharedKey>\n" +
                   "                <keyType>passPhrase</keyType>\n" +
                   "                <protected>false</protected>\n" +
                   "                <keyMaterial>" + password + "</keyMaterial>\n" +
                   "            </sharedKey>\n" +
                   "        </security>\n" +
                   "    </MSM>\n" +
                   "</WLANProfile>";
        } else {
            // Open Network Profile
            return "<?xml version=\"1.0\"?>\n" +
                   "<WLANProfile xmlns=\"http://www.microsoft.com/networking/WLAN/profile/v1\">\n" +
                   "    <name>" + ssid + "</name>\n" +
                   "    <SSIDConfig>\n" +
                   "        <SSID>\n" +
                   "            <hex>" + ssidHex + "</hex>\n" +
                   "            <name>" + ssid + "</name>\n" +
                   "        </SSID>\n" +
                   "    </SSIDConfig>\n" +
                   "    <connectionType>ESS</connectionType>\n" +
                   "    <connectionMode>auto</connectionMode>\n" +
                   "    <MSM>\n" +
                   "        <security>\n" +
                   "            <authEncryption>\n" +
                   "                <authentication>open</authentication>\n" +
                   "                <encryption>none</encryption>\n" +
                   "                <useOneX>false</useOneX>\n" +
                   "            </authEncryption>\n" +
                   "        </security>\n" +
                   "    </MSM>\n" +
                   "</WLANProfile>";
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    private String runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMsg = "Command failed with exit code " + exitCode + ": " + String.join(" ", command);
            log(errorMsg);
            System.err.println(errorMsg);
            System.err.println("Output:\n" + output);
        }

        return output.toString();
    }
}
