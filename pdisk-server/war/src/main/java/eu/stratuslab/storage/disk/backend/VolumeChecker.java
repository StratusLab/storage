package eu.stratuslab.storage.disk.backend;

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
		try {
			Integer nbLuns = backEndStorage.getNumberOfMappedLuns(volume);			
			VolumeChooser.getInstance().updateVolume(volume, nbLuns);
			logger.info("::: VolumeChecker: Volume '" + volume + "' was updated with " + nbLuns);
		} catch (Throwable th) {
			logger.warning("Unable to periodically update volume '" + volume
					+ "', cause: " + th);
		}
	}

	public String getVolume() {
		return volume;
	}

}
