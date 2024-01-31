package it.usna.shellyscan.view.util;

import it.usna.shellyscan.model.device.ShellyAbstractDevice;
import it.usna.shellyscan.model.device.modules.RelayCommander;
import it.usna.shellyscan.model.device.modules.RelayInterface;

public class UtilMiscellaneous {
	private UtilMiscellaneous() {}
	
	public static String getDescName(ShellyAbstractDevice d) {
		final String dName = d.getName();
		return (dName != null && dName.length() > 0 ? dName : d.getHostname());
	}
	
	public static String getDescName(ShellyAbstractDevice d, int channel) {
		if(d instanceof RelayCommander) {
			RelayInterface[] ri = ((RelayCommander)d).getRelays();
			if(ri != null) { // cover?
				String name;
				RelayInterface rel;
				/*if(channel >= ri.length) {
					return getDescName(d);
				} else*/
				if(channel < ri.length && (rel = ri[channel]) != null && (name = rel.getName()) != null && name.length() > 0) {
					final String dName = d.getName();
					return (dName != null && dName.length() > 0) ? dName + "-" + name : name;
				}
			}
		}
		return channel == 0 ? getDescName(d) : getDescName(d) + "-" + (channel + 1);
	}
	
	public static String getDescName(ShellyAbstractDevice d, String label) {
		return (label != null && label.isEmpty() == false) ? getDescName(d) : getDescName(d) + "-" + label;
	}

	public static String getFullName(ShellyAbstractDevice d) {
		final String dName = d.getName();
		if(dName.length() > 0) {
			return dName + "-" + d.getHostname() + "-" + d.getTypeName();
		} else {
			return d.getHostname() + "-" + d.getTypeName();
		}
	}
	
	public static String getExtendedHostName(ShellyAbstractDevice d) {
		final String dName = d.getName();
		return d.getHostname() + " - " + (dName != null && dName.length() > 0 ? dName : d.getTypeName());
	}
}