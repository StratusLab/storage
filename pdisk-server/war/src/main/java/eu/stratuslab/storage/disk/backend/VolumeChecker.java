package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class VolumeChecker {

	private static final Logger logger = Logger.getLogger(VolumeChecker.class
			.getName());

	private BackEndStorage backEndStorage;
	private String volume;

	public VolumeChecker(BackEndStorage backEndStorage, String volume) {
		this.volume = volume;
		this.backEndStorage = backEndStorage;
		logger.info("::: VolumeChecker created for Volume '" + volume + "'");
	}

	public void update() {		
		Integer nbLuns = backEndStorage.getNumberOfMappedLuns(volume);
		
		Map<String, Integer> singleUpdate = new HashMap<String, Integer>();
		singleUpdate.put(this.volume, nbLuns);
		
		VolumeChooser.getInstance().updateVolumes(singleUpdate);
		logger.info("::: VolumeChecker: Volume '" + volume + "' was updated with " + nbLuns);
	}

}
