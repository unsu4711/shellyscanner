package it.usna.shellyscan.model.device.g1.modules;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;

import it.usna.shellyscan.model.device.g1.AbstractG1Device;
import it.usna.shellyscan.model.device.modules.WIFIManager;

public class WIFIManagerG1 implements WIFIManager {
	private String net;
	private final AbstractG1Device d;
	private boolean enabled;
	private String dSSID;
	private boolean staticIP;
	private String ip;
	private String netmask;
	private String gw;
	private String dns;
	
	public WIFIManagerG1(AbstractG1Device d, Network network) throws IOException {
		this.d = d;
		if(network != null) {
			if(network == Network.PRIMARY) {
				net = "sta";
			} else if(network == Network.SECONDARY) {
				net = "sta1";
			} else {
				net = "err";
			}
			init();
		}
	}

	public WIFIManagerG1(AbstractG1Device d, Network network, boolean noInit) throws IOException {
		net = (network == Network.PRIMARY) ? "sta" : "sta1";
		this.d = d;
		if(noInit == false) {
			init();
		}
	}
	
	private void init() throws IOException {
		JsonNode sta = d.getJSON("/settings/" + net);
		enabled = sta.get("enabled").asBoolean();
		dSSID = sta.path("ssid").asText("");
		staticIP = sta.path("ipv4_method").asText().equals("static");
		ip = sta.path("ip").asText("");
		netmask = sta.path("mask").asText("");
		gw = sta.path("gw").asText("");
		dns = sta.path("dns").asText("");
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public String getSSID() {
		return dSSID;
	}

	@Override
	public boolean isStaticIP() {
		return staticIP;
	}

	@Override
	public String getIP() {
		return ip;
	}

	@Override
	public String getMask() {
		return netmask;
	}

	@Override
	public String getGateway() {
		return gw;
	}

	@Override
	public String getDNS() {
		return dns;
	}
	
	@Override
	public String disable() {
		return d.sendCommand("/settings/" + net + "?enabled=false");
	}

	@Override
	public String set(String ssid, String pwd) {
		try {
			String cmd = "/settings/" + net + "?enabled=true" +
					"&ssid=" + URLEncoder.encode(ssid, StandardCharsets.UTF_8.name()) +
					"&key=" + URLEncoder.encode(pwd, StandardCharsets.UTF_8.name()) +
					"&ipv4_method=dhcp";
			return d.sendCommand(cmd);
		} catch (UnsupportedEncodingException e) {
			return e.getMessage();
		}
	}
	
	@Override
	public String set(String ssid, String pwd, String ip, String netmask, String gw, String dns) {
		try {
			String cmd = "/settings/" + net + "?enabled=true" +
					"&ssid=" + URLEncoder.encode(ssid, StandardCharsets.UTF_8.name()) +
					"&key=" + URLEncoder.encode(pwd, StandardCharsets.UTF_8.name()) +
					"&ipv4_method=static" +
					"&ip=" + ip +
					"&netmask=" + netmask + // netmask = mask
					"&gateway=" + gw; // gateway = gw
			if(dns.length() > 0) {
				cmd += "&dns=" + dns;
			}
			return d.sendCommand(cmd);
		} catch (UnsupportedEncodingException e) {
			return e.getMessage();
		}
	}
	
	@Override
	public String enableRoaming(boolean enable) {
		return d.sendCommand("/settings?ap_roaming_enabled=" + enable);
	}
	
	public static Network currentConnection(AbstractG1Device d) {
		try {
			JsonNode settings = d.getJSON("/settings");
			JsonNode sta;
			if((sta = settings.get("wifi_sta")).get("enabled").asBoolean() && sta.get("ssid").asText("").equals(d.getSSID())) {
				return Network.PRIMARY;
			} else if((sta = settings.get("wifi_sta1")).get("enabled").asBoolean() && sta.get("ssid").asText("").equals(d.getSSID())) {
				return Network.SECONDARY;
			} else {
				return Network.AP;
			}
		} catch(Exception e) {
			return Network.UNKNOWN;
		}
	}
	
	public String restore(JsonNode sta, String pwd) throws UnsupportedEncodingException {
		if(sta.get("enabled").asBoolean()) {
			return d.sendCommand("/settings/" + net + "?enabled=true&" + AbstractG1Device.jsonNodeToURLPar(sta, "ssid", "ipv4_method", "ip", "dns") +
					"&key=" + URLEncoder.encode(pwd, StandardCharsets.UTF_8.name()) + "&netmask=" + sta.get("mask").asText("") + "&gateway=" + sta.get("gw").asText(""));
		} else {
			return disable();
		}
	}
	
	public static String restoreRoam(AbstractG1Device d, JsonNode roam) {
		return d.sendCommand("/settings?ap_roaming_enabled=" + roam.path("enabled").asBoolean() + "&ap_roaming_threshold=" + roam.path("threshold").asText());
	}
}
