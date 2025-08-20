package it.usna.shellyscan.prov;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

public class ShellyApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SHELLY_DEFAULT_IP = "192.168.33.1";

    public ShellyApiClient() {
        this.httpClient = new HttpClient();
        try {
            this.httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start HttpClient", e);
        }
    }

    public boolean configureShellyWifi(String homeSsid, String homePassword) {
        // Try Gen2/3 RPC API First
        try {
            String url = "http://" + SHELLY_DEFAULT_IP + "/rpc/WiFi.SetConfig";
            String jsonPayload = objectMapper.writeValueAsString(new WifiSetConfigRequest(homeSsid, homePassword));
            
            Request request = httpClient.POST(url).body(new StringRequestContent(jsonPayload));
            var response = request.send();

            if (response.getStatus() == 200) {
                System.out.println("Successfully configured Wi-Fi via Gen2/3 API.");
                return true;
            } else if (response.getStatus() == 404) {
                System.out.println("Gen2/3 API not found (404), falling back to Gen1 API.");
            } else {
                 System.err.println("Error with Gen2/3 API: " + response.getStatus());
            }
        } catch (Exception e) {
            System.err.println("Error during Gen2/3 configuration, falling back. Error: " + e.getMessage());
        }

        // Fallback to Gen1 API
        try {
            String encodedSsid = URLEncoder.encode(homeSsid, StandardCharsets.UTF_8);
            String encodedPassword = URLEncoder.encode(homePassword, StandardCharsets.UTF_8);
            String url = String.format("http://%s/settings/sta?ssid=%s&key=%s&enabled=1",
                    SHELLY_DEFAULT_IP, encodedSsid, encodedPassword);

            var response = httpClient.newRequest(url).method(HttpMethod.GET).send();
            
            if (response.getStatus() == 200) {
                System.out.println("Successfully configured Wi-Fi via Gen1 API.");
                return true;
            } else {
                System.err.println("Error configuring with Gen1 API: " + response.getStatus());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Failed to configure with Gen1 API. Error: " + e.getMessage());
            return false;
        }
    }

    public ShellyDeviceInfo getShellyDeviceInfo(String ipAddress) {
        // Try Gen2+ /shelly endpoint first
        try {
            String url = "http://" + ipAddress + "/shelly";
            var response = httpClient.GET(url);
            if (response.getStatus() == 200) {
                return objectMapper.readValue(response.getContentAsString(), ShellyDeviceInfo.class);
            }
        } catch (Exception e) {
            // Fall through to Gen1
        }

        // Fallback to Gen1 /settings endpoint
        try {
            String url = "http://" + ipAddress + "/settings";
            var response = httpClient.GET(url);
            if (response.getStatus() == 200) {
                JsonNode root = objectMapper.readTree(response.getContentAsString());
                JsonNode deviceNode = root.path("device");
                return new ShellyDeviceInfo(
                    deviceNode.path("mac").asText(),
                    deviceNode.path("type").asText(),
                    "1", // Gen1
                    null, null
                );
            }
        } catch (Exception e) {
             System.err.println("Failed to get device info from " + ipAddress + ". Error: " + e.getMessage());
        }

        return null;
    }

    public boolean waitForShellyApReachable(int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;
        String url = "http://" + SHELLY_DEFAULT_IP + "/shelly";

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                var response = httpClient.GET(url);
                if (response.getStatus() == 200) {
                    System.out.println("Shelly AP is reachable.");
                    return true;
                }
            } catch (Exception e) {
                // Ignore and retry
            }
            try {
                Thread.sleep(2000); // Wait 2 seconds before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        System.err.println("Shelly AP did not become reachable within " + timeoutSeconds + " seconds.");
        return false;
    }

    public boolean setShellyDeviceName(String ipAddress, int generation, String name) {
        try {
            if (generation >= 2) {
                // Gen2+ RPC API
                String url = "http://" + ipAddress + "/rpc/Sys.SetConfig";
                String jsonPayload = objectMapper.writeValueAsString(Map.of("config", Map.of("device", Map.of("name", name))));
                var response = httpClient.POST(url).body(new StringRequestContent(jsonPayload)).send();
                return response.getStatus() == 200;
            } else {
                // Gen1 API
                 String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
                String url = String.format("http://%s/settings?name=%s", ipAddress, encodedName);
                var response = httpClient.GET(url);
                return response.getStatus() == 200;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Inner class for Gen2/3 WiFi.SetConfig JSON structure
    private static class WifiSetConfigRequest {
        public Map<String, Object> config;
        public boolean ap_roaming = true;

        public WifiSetConfigRequest(String ssid, String pass) {
            this.config = Map.of(
                "sta", Map.of(
                    "ssid", ssid,
                    "pass", pass,
                    "enable", true
                )
            );
        }
    }

    // Record for holding device info, using Jackson annotations
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShellyDeviceInfo(
        String mac,
        String model,
        String gen,
        String fw_id,
        String ver
    ) {}
}
