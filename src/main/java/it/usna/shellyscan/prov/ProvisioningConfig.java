package it.usna.shellyscan.prov;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.File;
import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProvisioningConfig {

    @JsonProperty("HOME_WIFI_SSID")
    private String homeWifiSsid;

    @JsonProperty("HOME_WIFI_PASSWORD")
    private String homeWifiPassword;

    @JsonProperty("GLOBAL_LOG_LEVEL")
    private String globalLogLevel;

    @JsonProperty("SHELLY_HOME_IP_SCAN_BASE")
    private String shellyHomeIpScanBase;

    @JsonProperty("SHELLY_HOME_IP_SCAN_RANGE_START")
    private int shellyHomeIpScanRangeStart;

    @JsonProperty("SHELLY_HOME_IP_SCAN_RANGE_END")
    private int shellyHomeIpScanRangeEnd;

    @JsonProperty("SCAN_LOOP_DELAY_SECONDS")
    private int scanLoopDelaySeconds;

    // Getters
    public String getHomeWifiSsid() { return homeWifiSsid; }
    public String getHomeWifiPassword() { return homeWifiPassword; }
    public String getGlobalLogLevel() { return globalLogLevel; }
    public String getShellyHomeIpScanBase() { return shellyHomeIpScanBase; }
    public int getShellyHomeIpScanRangeStart() { return shellyHomeIpScanRangeStart; }
    public int getShellyHomeIpScanRangeEnd() { return shellyHomeIpScanRangeEnd; }
    public int getScanLoopDelaySeconds() { return scanLoopDelaySeconds; }

    
	public static ProvisioningConfig load() {
		ObjectMapper mapper = new ObjectMapper();
		File configFile = new File("config.json");

		try {
			if (configFile.exists()) {
				return mapper.readValue(configFile, ProvisioningConfig.class);
			}
		} catch (IOException e) {
			System.err.println("Error reading or parsing config.json: " + e.getMessage());
			// Fall through to return default config
		}
		
		System.err.println("config.json not found or failed to parse. Using default configuration.");
		return createDefaultConfig();
	}

	private static ProvisioningConfig createDefaultConfig() {
		ProvisioningConfig config = new ProvisioningConfig();
		config.homeWifiSsid = "your_home_wifi_ssid";
		config.homeWifiPassword = "your_home_wifi_password";
		config.globalLogLevel = "INFO";
		config.shellyHomeIpScanBase = "192.168.1.";
		config.shellyHomeIpScanRangeStart = 1;
		config.shellyHomeIpScanRangeEnd = 254;
		config.scanLoopDelaySeconds = 120; // Default from python script
		return config;
	}
}
