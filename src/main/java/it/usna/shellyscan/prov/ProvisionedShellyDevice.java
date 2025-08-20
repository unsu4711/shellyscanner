package it.usna.shellyscan.prov;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvisionedShellyDevice(
    @JsonProperty("bssid") String bssid,
    @JsonProperty("status") String status,
    @JsonProperty("first_seen") String firstSeen,
    @JsonProperty("last_seen") String lastSeen,
    @JsonProperty("failure_reason") String failureReason,
    @JsonProperty("device_id") String deviceId,
    @JsonProperty("device_type") String deviceType,
    @JsonProperty("home_ip_address") String homeIpAddress,
    @JsonProperty("mac_address") String macAddress,
    @JsonProperty("initial_firmware_version") String initialFirmwareVersion,
    @JsonProperty("name") String name
) {
    public ProvisionedShellyDevice withStatus(String newStatus) {
        return new ProvisionedShellyDevice(
            this.bssid, newStatus, this.firstSeen, this.lastSeen, this.failureReason,
            this.deviceId, this.deviceType, this.homeIpAddress, this.macAddress,
            this.initialFirmwareVersion, this.name
        );
    }
}
