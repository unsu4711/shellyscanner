package it.usna.shellyscan.model.device.modules;

public interface MQTTManager {
	
	boolean isEnabled();
	
	String getServer();
	
	String getUser();
	
	String getPrefix();
	
	String disable();
	
	String set(String server, String user, String pwd, String prefix);
}
