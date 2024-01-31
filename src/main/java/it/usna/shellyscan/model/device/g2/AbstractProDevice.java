package it.usna.shellyscan.model.device.g2;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import it.usna.shellyscan.model.Devices;

public abstract class AbstractProDevice extends AbstractG2Device {

	protected AbstractProDevice(InetAddress address, int port, String hostname) {
		super(address, port, hostname);
	}
	
	@Override
	void restoreCommonConfig(JsonNode config, final long delay, Map<Restore, String> data, List<String> errors) throws InterruptedException, IOException {
		super.restoreCommonConfig(config, delay, data, errors);
		errors.add(ethRestore(config.get("eth")));
	}
	
	private String ethRestore(JsonNode eth) throws JsonProcessingException, InterruptedException {
		TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);
		return postCommand("Eth.SetConfig", "{\"config\":" + jsonMapper.writeValueAsString(eth) + "}");
	}
}

/*
eth" : {
"enable" : true,
"ipv4mode" : "dhcp",
"ip" : null,
"netmask" : null,
"gw" : null,
"nameserver" : null
},
*/