package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import eu.stratuslab.storage.disk.utils.DiskUtils;

public class BackendChecker implements Runnable {

	private final static long UPDATE_SLEEP = 6000;
	private Map<String, Integer> volumes = new HashMap<String, Integer>();

	private static final Logger logger = Logger.getLogger("org.restlet");

	public BackendChecker() {
		log("Starting...");
		update();
		log("Started.");
	}

	@Override
	public void run() {
		while (true) {
			update();
			sleep();
		}
	}

	private void update() {
		getMappedLunsInVolumes();
		updateVolumeChooser();
	}

	private void updateVolumeChooser() {
		log("Updating VolumeChooser.");
		VolumeChooser vc = VolumeChooser.getInstance();
		vc.updateVolumes(volumes);
	}

	private void getMappedLunsInVolumes() {
		log("Getting mapped LUNs in volumes...");
		BackEndStorage backend = new BackEndStorage();
		for (String volume : DiskUtils.getBackendProxiesFromConfig()) {
			volume = volume.trim();
			if (volume != null && !volume.isEmpty()) {
				log("Volume '" + volume + "'");
    			int luns = backend.getNumberOfMappedLuns(volume);
    			log("Volume  " + volume + " has " + luns + " mapped LUNs.");
    			volumes.put(volume, luns);
			}
		}
	}

	private void sleep() {
		try {
			log("Sleeping " + UPDATE_SLEEP / 1000 + " sec.");
			Thread.sleep(UPDATE_SLEEP);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void log(String msg) {
		logger.info("::: BackendChecker: " + msg);
    }
}
