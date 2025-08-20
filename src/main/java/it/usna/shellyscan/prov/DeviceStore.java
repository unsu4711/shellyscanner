package it.usna.shellyscan.prov;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceStore {

    private static final String DEVICES_FILE = "shelly_devices.json";
    private final ObjectMapper objectMapper;
    private final File devicesFile;

    public DeviceStore() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.devicesFile = new File(DEVICES_FILE);
    }

    public Map<String, ProvisionedShellyDevice> loadDiscoveredDevices() {
        if (!devicesFile.exists()) {
            return new ConcurrentHashMap<>();
        }
        try {
            TypeReference<ConcurrentHashMap<String, ProvisionedShellyDevice>> typeRef 
                = new TypeReference<>() {};
            return objectMapper.readValue(devicesFile, typeRef);
        } catch (IOException e) {
            System.err.println("Error reading or parsing " + DEVICES_FILE + ": " + e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    public void saveDiscoveredDevices(Map<String, ProvisionedShellyDevice> devices) {
        try {
            objectMapper.writeValue(devicesFile, devices);
        } catch (IOException e) {
            System.err.println("Error saving device list to " + DEVICES_FILE + ": " + e.getMessage());
        }
    }
}
