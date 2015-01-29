package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

	public synchronized String requestVolumeName() {
		checkVolumesKnown();
		return requestVolumeNameFrom(volumes.keySet());
	}

	public synchronized String requestVolumeNameFrom(Set<String> volumeNames) {
		checkVolumesKnown();
		String bestCandidate = findVolumeNameLeastFilledIn(volumeNames);
		if (volumes.get(bestCandidate) >= maxLUN) {
			noResultException();
		}

		volumes.replace(bestCandidate, volumes.get(bestCandidate) + 1);
		return bestCandidate;
	}

	public synchronized void releaseVolume(String volumeName) {
		checkVolumesKnown();

		if (!volumes.containsKey(volumeName)) {
			// log ignored request
			return;
		}

		int currentValue = volumes.get(volumeName);
		int newValue = currentValue - 1;
		if (newValue < 0) {
			newValue = 0;
		}
		volumes.put(volumeName, newValue);
	}

	public synchronized void updateVolumes(Map<String, Integer> newVolumes) {
		if (volumes == null) {
			this.volumes = new HashMap<String, Integer>(newVolumes);
		} else {
			this.volumes.putAll(newVolumes);
		}
	}

	public double percentageConsumed() {
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
	
	private String findVolumeNameLeastFilledIn(Set<String> volumeNames) {
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
			noResultException();
		}

		return result;
	}

	private void checkVolumesKnown() {
		if (this.volumes == null || this.volumes.isEmpty()) {
			throw new IllegalStateException("Volumes states unknown");
		}
	}

	private void noResultException() {
		throw new IllegalStateException("Unable to fullfill request");
	}

}