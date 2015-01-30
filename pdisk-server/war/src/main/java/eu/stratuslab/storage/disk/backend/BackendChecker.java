package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import eu.stratuslab.storage.disk.utils.DiskUtils;

/**
 * Instantiate and call update() to fully initiate the instance, then feed it to
 * a thread runner.
 *
 */
public class BackendChecker implements Runnable {

	private final static long UPDATE_SLEEP = 60000;
	private Map<String, Integer> volumes = new HashMap<String, Integer>();

	private static final Logger logger = Logger.getLogger("BackendChecker");

	private BackEndStorage backEndStorage = DiskUtils.getDiskStorage();

	public BackendChecker() {
	}

	@Override
	public void run() {
		while (true) {
			update();
			sleep();
		}
	}

	public void update() {
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
		for (String volume : getBackendProxiesFromConfig()) {
			if (volume != null) {
				volume = volume.trim();
				if (!volume.isEmpty()) {
					int luns = getNumberOfMappedLuns(volume);
					log("Volume '" + volume + "' has " + luns + " mapped LUNs.");
					volumes.put(volume, luns);
				}
			}
		}
		log("Found Vol/mappedLUNs: " + volumes);
	}

	protected Integer getNumberOfMappedLuns(String volume) {
		log("Getting # of mapped LUNs in volume '" + volume + "'");
		return backEndStorage.getNumberOfMappedLuns(volume);
	}

	protected String[] getBackendProxiesFromConfig() {
		return DiskUtils.getBackendProxiesFromConfig();
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
