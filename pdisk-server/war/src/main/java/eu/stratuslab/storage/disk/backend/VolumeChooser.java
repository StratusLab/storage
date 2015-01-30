package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
* Chooses the least filled volume (according to its own known state).
* The state must first be updated (by a "Checker") before any request.
* The state as updated by an external Checker takes precedence over the one internally maintained.
*
* The representation of the state is a map (String, Integer):
* {volumeName1 nbLogicalUnits1, volumeName2 nbLogicalUnits2, ...}
*
* Please see Unit tests for exact behavior.
*/
public final class VolumeChooser {

	private static final Logger logger = Logger.getLogger(VolumeChooser.class.getName());
	
	private static final int MIN_RETRY_WAIT_MS = 500; 
	private static final int MAX_RETRY_WAIT_MS = 1500; 
	
	private static final int MAX_LUN = 512;
	// TODO read in config file in constructor

	private static VolumeChooser instance = new VolumeChooser(MAX_LUN);

	protected Map<String, Integer> volumes;

	protected int maxLUN;

	private VolumeChooser(int maxLun) {
		this.maxLUN = maxLun;
	}

	public static VolumeChooser getInstance() {
		return instance;
	}

	public String requestVolumeNameWithRetry() {		
		return requestVolumeNameWithRetryFrom(volumes.keySet().toArray(new String[0]));		
	}
	
	public String requestVolumeNameWithRetryFrom(String[] volumeNames) {
		try {
			return requestVolumeNameFrom(volumeNames);
		} catch (IllegalStateException ise) {
			
			long randomTimeToWait = (long)(MIN_RETRY_WAIT_MS + Math.random()*(MAX_RETRY_WAIT_MS-MIN_RETRY_WAIT_MS));			
			logger.info("Failed, will retry in "+ randomTimeToWait + " ms");			
			try {
				Thread.sleep(randomTimeToWait);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return requestVolumeNameFrom(volumeNames);			
		}
	}
	
	public synchronized String requestVolumeName() {
		checkVolumesKnown();		
		return requestVolumeNameFrom(volumes.keySet().toArray(new String[0]));
	}

	public synchronized String requestVolumeNameFrom(String[] volumeNames) {
		checkVolumesKnown();
		String bestCandidate = findVolumeNameLeastFilledIn(volumeNames);
		if (volumes.get(bestCandidate) >= maxLUN) {
			noResultException(bestCandidate + " volume is already full.");
		}

		volumes.put(bestCandidate, volumes.get(bestCandidate) + 1);
		return bestCandidate;
	}

	public synchronized void releaseVolume(String volumeName) {
		checkVolumesKnown();

		if (!volumes.containsKey(volumeName)) {
			logger.info("Releasing unknown volume '" + volumeName + "'");
			return;
		}

		int currentValue = volumes.get(volumeName);
		int newValue = currentValue - 1;
		if (newValue < 0) {
			newValue = 0;
		}
		volumes.put(volumeName, newValue);
		logger.info("Released '" + volumeName + "'");
	}

	public synchronized void updateVolumes(Map<String, Integer> newVolumes) {
		if (volumes == null) {
			this.volumes = new HashMap<String, Integer>(newVolumes);
		} else {
			this.volumes.putAll(newVolumes);
		}
	}

	public synchronized double percentageConsumed() {
		if (volumes!=null) {
			int fullCapacity = volumes.size() * maxLUN;
			int consumed = 0;
			for (Integer v : volumes.values()) {
				consumed += v;
			}
			double percentage = (double)consumed/(double)fullCapacity;
			return percentage;
		} else {
			return -1;
		}
	}

	private String findVolumeNameLeastFilledIn(String[] volumeNames) {
		String result = null;
		int best = 0;
		for (String volumeName : volumeNames) {
			Integer current = volumes.get(volumeName);
			if (current != null && (result == null || best > current)) {
				result = volumeName;
				best = current;
			}
		}

		if (result == null) {
			noResultException("Unable to find any free volume for LUN allocation.");
		}

		return result;
	}

	private void checkVolumesKnown() {
		if (this.volumes == null || this.volumes.isEmpty()) {
			throw new IllegalStateException("Volumes states unknown");
		}
	}

	private void noResultException(String msg) {
		throw new IllegalStateException(msg);
	}

}
