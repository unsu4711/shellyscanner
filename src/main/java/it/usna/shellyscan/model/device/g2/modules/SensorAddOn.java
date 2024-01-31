package it.usna.shellyscan.model.device.g2.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.usna.shellyscan.model.Devices;
import it.usna.shellyscan.model.device.Meters;
import it.usna.shellyscan.model.device.ShellyAbstractDevice.Restore;
import it.usna.shellyscan.model.device.g2.AbstractG2Device;

/**
 * Sensor add-on model<br>
 * @see https://kb.shelly.cloud/knowledge-base/shelly-plus-add-on
 * @see https://shelly-api-docs.shelly.cloud/gen2/Addons/ShellySensorAddon
 * @author usna
 */
public class SensorAddOn extends Meters {
	private final static Logger LOG = LoggerFactory.getLogger(SensorAddOn.class);
	public final static String BACKUP_SECTION = "SensorAddon.GetPeripherals.json";
	public final static String MSG_RESTORE_ERROR = "msgRestoreSensorAddOn";
	public final static String ADDON_TYPE = "sensor";
	private Type[] supported;

	private String extT0ID, extT1ID, extT2ID, extT3ID, extT4ID;
	private float extT0, extT1, extT2, extT3, extT4;
	private String extT0Name, extT1Name, extT2Name, extT3Name, extT4Name;

	private String humidityID;
	private int humidity;
	private String humidityName;

	private String switchID;
	private boolean switchOn;
	private String switchName;

	private String analogID;
	private float analog;
	private String analogName;

	private String voltmeterID;
	private float volt;
	private String voltmeterName;

	public SensorAddOn(AbstractG2Device d) throws IOException {
		try {
			JsonNode peripherals = d.getJSON("/rpc/SensorAddon.GetPeripherals");
			ArrayList<Meters.Type> types = new ArrayList<>();

			ObjectNode dht22Node = ((ObjectNode)peripherals.get("dht22"));
			if(dht22Node.size() > 0) {
				Iterator<String> dht22 = dht22Node.fieldNames();
				while(dht22.hasNext()) {
					String par = dht22.next();
					if(par.startsWith("temperature")) {
						extT0ID = par;
					} else if(par.startsWith("humidity")) {
						humidityID = par;
					}
				}
				types.add(Type.T);
				types.add(Type.H);
			}
			ObjectNode ds18b20Node = ((ObjectNode)peripherals.get("ds18b20"));
			if(ds18b20Node.size() > 0) {
				Iterator<String> temp = ds18b20Node.fieldNames();
				for(int i = 0; temp.hasNext(); i++) {
					if(i == 0) {
						extT0ID = temp.next();
						types.add(Type.T);
					} else if(i == 1) {
						extT1ID = temp.next();
						types.add(Type.T1);
					} else if(i == 2) {
						extT2ID = temp.next();
						types.add(Type.T2);
					} else if(i == 3) {
						extT3ID = temp.next();
						types.add(Type.T3);
					} else if(i == 4) {
						extT4ID = temp.next();
						types.add(Type.T4);
					}
				}
			}

			Iterator<String> digIn = ((ObjectNode)peripherals.get("digital_in")).fieldNames();
			if(digIn.hasNext()) {
				switchID = digIn.next();
				types.add(Type.EX);
			}
			Iterator<String> analogIn = ((ObjectNode)peripherals.get("analog_in")).fieldNames();
			if(analogIn.hasNext()) {
				analogID = analogIn.next();
				types.add(Type.PERC);
			}
			Iterator<String> voltIn = ((ObjectNode)peripherals.get("voltmeter")).fieldNames();
			if(voltIn.hasNext()) {
				voltmeterID = voltIn.next();
				types.add(Type.V);
			}
			supported = types.toArray(Type[]::new);
		} catch (RuntimeException e) {
			supported = new Type[0];
			LOG.error("Add-on init error", e);
		}
	}

	@Override
	public Type[] getTypes() {
		return supported;
	}
	
	public void fillSettings(JsonNode configuration) throws IOException {
		try {
			JsonNode cnf;
			if(switchID != null && (cnf = configuration.get(switchID)) != null) {
				switchName = cnf.path("name").textValue();
			}
			if(analogID != null && (cnf = configuration.get(analogID)) != null) {
				analogName = cnf.path("name").textValue();
			}
			if(voltmeterID != null && (cnf = configuration.get(voltmeterID)) != null) {
				voltmeterName = cnf.path("name").textValue();
			}
			if(extT0ID != null && (cnf = configuration.get(extT0ID)) != null) {
				extT0Name = cnf.path("name").textValue();
			}
			if(extT1ID != null && (cnf = configuration.get(extT1ID)) != null) {
				extT1Name = cnf.path("name").textValue();
			}
			if(extT2ID != null && (cnf = configuration.get(extT2ID)) != null) {
				extT2Name = cnf.path("name").textValue();
			}
			if(extT3ID != null && (cnf = configuration.get(extT3ID)) != null) {
				extT3Name = cnf.path("name").textValue();
			}
			if(extT4ID != null && (cnf = configuration.get(extT4ID)) != null) {
				extT4Name = cnf.path("name").textValue();
			}
			if(humidityID != null && (cnf = configuration.get(humidityID)) != null) {
				humidityName = cnf.path("name").textValue();
			}
		} catch (RuntimeException e) {
			LOG.warn("Settings Add-on configuration changed?", e);
		}	
	}

	public void fillStatus(JsonNode status) {
		try {
			if(switchID != null) {
				switchOn = status.path(switchID).get("state").asBoolean();
			}
			if(analogID != null) {
				analog = status.path(analogID).get("percent").floatValue();
			}
			if(voltmeterID != null) {
				volt = status.path(voltmeterID).get("voltage").floatValue();
			}
			if(extT0ID != null) {
				extT0 = status.path(extT0ID).get("tC").floatValue();
			}
			if(extT1ID != null) {
				extT1 = status.path(extT1ID).get("tC").floatValue();
			}
			if(extT2ID != null) {
				extT2 = status.path(extT2ID).get("tC").floatValue();
			}
			if(extT3ID != null) {
				extT3 = status.path(extT3ID).get("tC").floatValue();
			}
			if(extT4ID != null) {
				extT4 = status.path(extT4ID).get("tC").floatValue();
			}
			if(humidityID != null) {
				humidity = status.path(humidityID).get("rh").intValue();
			}
		} catch (RuntimeException e) {
			LOG.warn("Status Add-on configuration changed?", e);
		}
	}

	public boolean isDigitalInputOn() {
		return switchOn;
	}

	public float getAnalog() {
		return analog;
	}

	public float getVoltage() {
		return volt;
	}

	public float getTemp0() {
		return extT0;
	}

	public float getTemp1() {
		return extT1;
	}

	public float getTemp2() {
		return extT2;
	}

	public float getTemp3() {
		return extT3;
	}

	public float getTemp4() {
		return extT4;
	}

	public float gethumidity() {
		return humidity;
	}

	@Override
	public float getValue(Type t) {
		return switch(t) {
		case EX -> switchOn ? 1f : 0f;
		case PERC -> analog;
		case V -> volt;
		case T -> extT0;
		case T1 -> extT1;
		case T2 -> extT2;
		case T3 -> extT3;
		case T4 -> extT4;
		case H -> humidity;
		default -> 0;
		};
	}
	
	public String getName(Type t) {
		return switch(t) {
		case EX -> switchName;
		case PERC -> analogName;
		case V -> voltmeterName;
		case T -> extT0Name;
		case T1 -> extT1Name;
		case T2 -> extT2Name;
		case T3 -> extT3Name;
		case T4 -> extT4Name;
		case H -> humidityName;
		default -> "";
		};
	}

	public static String[] getInfoRequests(String [] cmd) {
		String[] newArray = Arrays.copyOf(cmd, cmd.length + 1);
		newArray[cmd.length] = "/rpc/SensorAddon.GetPeripherals";
		return newArray;
	}

	public static String enable(AbstractG2Device d, boolean enable) {
		return d.postCommand("Sys.SetConfig", "{\"config\":{\"device\":{\"addon_type\":" + (enable ? "\"sensor\"" : "null") + "}}}");
	}

	public static String addSensor(AbstractG2Device d, String type, String id) {
		// curl -X POST -d '{"id":1,"method":"SensorAddon.AddPeripheral","params":{"type":"digital_in","attrs":{"cid":100}}}'
		return d.postCommand("SensorAddon.AddPeripheral", "{\"type\":\"" + type + "\",\"attrs\":{\"cid\":" + id + "}}");
	}

	public static String addSensor(AbstractG2Device d, String type, String id, String addr) {
		// curl -X POST -d '{"id":1,"method":"SensorAddon.AddPeripheral","params":{"type":"ds18b20","attrs":{"cid":101,"addr":"11:22:33:44:55:66:77:88"}}}'
		return d.postCommand("SensorAddon.AddPeripheral", "{\"type\":\"" + type + "\",\"attrs\":{\"cid\":" + id + ",\"addr\":\"" + addr + "\"}}");
	}

	public static <T extends AbstractG2Device & SensorAddOnHolder> boolean restoreCheck(T d, Map<String, JsonNode> backupJsons, Map<Restore, String> res) {
		SensorAddOn addOn = d.getSensorAddOn();
		JsonNode backupAddOn = backupJsons.get(BACKUP_SECTION);
		return addOn == null || addOn.getTypes().length == 0 || backupAddOn == null || backupAddOn.size() == 0;
	}

	public static <T extends AbstractG2Device & SensorAddOnHolder> void restore(T d, Map<String, JsonNode> backupJsons, List<String> errors) throws InterruptedException {
		SensorAddOn addOn = d.getSensorAddOn();
		JsonNode backupAddOn = backupJsons.get(BACKUP_SECTION);
		//todo errors.add(Input.restore(this, configuration, "0"));
		if(backupAddOn == null && addOn != null) {
			errors.add(enable(d, false));
		} else if(backupAddOn != null) {
			if(addOn == null || addOn.getTypes().length == 0) { // no sensor defined -> go on
				if(addOn == null) {
					enable(d, true);
				}
//				JsonNode backupConfig = backupJsons.get("Shelly.GetConfig.json");
				Iterator<Entry<String, JsonNode>> nodes = backupAddOn.fields();
				while(nodes.hasNext()) {
					Map.Entry<String, JsonNode> entry = (Map.Entry<String, JsonNode>) nodes.next();
					if(entry.getValue() != null && entry.getValue().isEmpty() == false) {
						String sensor = entry.getKey();
						//System.out.println(sensor);
						Iterator<Entry<String, JsonNode>> id = entry.getValue().fields();
						while(id.hasNext()) {
							Entry<String, JsonNode> input = id.next();
							String inputKey = input.getKey();
							JsonNode inputValue = input.getValue();
							//System.out.println(id.next().getKey());
							TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);
							String typeIdx[] = inputKey.split(":");
							if(inputValue.has("addr")) {
								errors.add(addSensor(d, sensor, typeIdx[1], inputValue.get("addr").asText()));
							} else {
								errors.add(addSensor(d, sensor, typeIdx[1]));
							}
							// device should reboot before
//							if("input".equals(typeIdx[0])) {
//								TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);
//								errors.add(Input.restore(d, backupConfig, typeIdx[1]));
//							}
						}
					}
				}
			}
		}
	}
	
//	@Override
//	public String toString() {
//		if(supported.length > 0) {
//			String res = supported[0] + ":" + NF.format(getValue(supported[0]));
//			for(int i = 1; i < supported.length; i++) {
//				res += " " + supported[i] + ":" + NF.format(getValue(supported[i]));
//			}
//			return res;
//		} else {
//			return "";
//		}
//	}
}

//todo Gen2 fw 1.0.0 - Input invert and range_map configuration properties for analog input type