package it.usna.shellyscan.prov;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class ShellyMdnsDiscoverer {

    private static final String SERVICE_TYPE = "_shelly._tcp.local.";
    private final Map<String, String> discoveredDevices = new ConcurrentHashMap<>();

    public Map<String, String> discover(int timeoutSeconds) {
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            jmdns.addServiceListener(SERVICE_TYPE, new ShellyServiceListener());

            System.out.println("Starting mDNS discovery for " + timeoutSeconds + " seconds...");
            Thread.sleep(timeoutSeconds * 1000);

            jmdns.close();
            System.out.println("mDNS discovery finished.");
            return Collections.unmodifiableMap(discoveredDevices);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private class ShellyServiceListener implements ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            // Request resolution of the service
            event.getDNS().requestServiceInfo(event.getType(), event.getName(), true);
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            discoveredDevices.remove(event.getName());
            System.out.println("Service removed: " + event.getName());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            String ipAddress = event.getInfo().getInet4Addresses().length > 0 ? event.getInfo().getInet4Addresses()[0].getHostAddress() : null;
            if (ipAddress != null) {
                System.out.println("Service resolved: " + event.getName() + " at " + ipAddress);
                discoveredDevices.put(event.getName(), ipAddress);
            }
        }
    }
}
