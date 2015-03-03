package eu.stratuslab.storage.disk.backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;

/**
 * Serves volume names to concurrent requesters so that the load on each served
 * volume stays below a limit (maxLun). The strategy to serve these is to
 * distribute them as uniformly as possible. When no place is available,
 * exception is thrown.
 * 
 * Its initial state (the load for each volume) is updated by the backend
 * checker. This state is continuously updated.
 *
 * The representation of the state is a map (String, Integer): {volumeName1
 * nbLogicalUnits1, volumeName2 nbLogicalUnits2, ...}
 * 
 * This class is thread safe (simple way: mutator methods are synchronized).
 *
 * Please see Unit tests for exact behavior.
 */
public final class VolumeChooser {

	protected static final Logger logger = Logger.getLogger(VolumeChooser.class
			.getName());

	private static final int MIN_RETRY_WAIT_MS = ServiceConfiguration
			.getInstance().CHOOSER_MIN_RETRY_WAIT_MS;
	private static final int MAX_RETRY_WAIT_MS = ServiceConfiguration
			.getInstance().CHOOSER_MAX_RETRY_WAIT_MS;
	private static final int MAX_LUN = ServiceConfiguration.getInstance().ISCSI_MAX_LUN;

	private static VolumeChooser instance = new VolumeChooser(MAX_LUN);

	protected Map<String, Integer> volumes = new HashMap<String, Integer>();

	private final Map<String, Long> volumeDateChosen = new HashMap<String, Long>();

	protected int maxLUN;

	private VolumeChooser(int maxLun) {
		this.maxLUN = maxLun;
		logger.info("VolumeChooser::Max LUNs =" + maxLun);
		logger.info("VolumeChooser::min/max retry wait in ms="
				+ MIN_RETRY_WAIT_MS + "/" + MAX_RETRY_WAIT_MS);
	}

	public static VolumeChooser getInstance() {
		return instance;
	}

	public String requestVolumeNameWithRetry() {
		return requestVolumeNameWithRetryFrom(volumes.keySet().toArray(
				new String[0]));
	}

	public String requestVolumeNameWithRetryFrom(String[] volumeNames) {
		try {
			return requestVolumeNameFrom(volumeNames);
		} catch (ResourceException re) {

			long randomTimeToWait = (long) (MIN_RETRY_WAIT_MS + Math.random()
					* (MAX_RETRY_WAIT_MS - MIN_RETRY_WAIT_MS));
			logger.info("Failed, will retry in " + randomTimeToWait + " ms");
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
		String chosenVolume = findVolumeNameBelowLimitWithOldestAccess(volumeNames);
		updateLoad(volumeNames, chosenVolume);
		return chosenVolume;
	}

	private void updateLoad(String[] volumeNames, String chosenVolume) {
		int newValue = volumes.get(chosenVolume) + 1;
		volumes.put(chosenVolume, newValue);
		logger.info("Serving volume '" + chosenVolume + "', current LUN="
				+ newValue + " from " + Arrays.asList(volumeNames));
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
		logger.info("Released '" + volumeName + "', current LUN=" + newValue);
	}

	public synchronized void updateVolume(String volume, Integer lun) {
		volumes.put(volume, lun);
	}

	public synchronized double percentageConsumed() {
		if (!volumes.isEmpty()) {
			int fullCapacity = volumes.size() * maxLUN;
			int consumed = 0;
			for (Integer v : volumes.values()) {
				consumed += v;
			}
			double percentage = (double) consumed / (double) fullCapacity;
			return percentage;
		} else {
			return -1;
		}
	}

	private String findVolumeNameBelowLimitWithOldestAccess(String[] volumeNames) {
		checkValidRequest(volumeNames);
		List<String> candidates = filterBelowLimit(volumeNames);
		String result = findOldestAccess(candidates);
		remember(result);
		return result;
	}

	private String findOldestAccess(List<String> candidates) {
		String result = null;
		long oldestLastTimeChosen = -1;
		for (String candidate : candidates) {
			if (!volumeDateChosen.containsKey(candidate)) {
				result = candidate;
				break;
			} else {
				long lastTimeChosen = volumeDateChosen.get(candidate);
				if (oldestLastTimeChosen < 0
						|| lastTimeChosen < oldestLastTimeChosen) {
					oldestLastTimeChosen = lastTimeChosen;
					result = candidate;
				}
			}
		}
		return result;
	}

	private List<String> filterBelowLimit(String[] volumeNames) {
		List<String> candidates = new ArrayList<String>();
		for (String volumeName : volumeNames) {
			Integer current = volumes.get(volumeName);
			if (current != null && current < maxLUN) {
				candidates.add(volumeName);
			}
		}
		if (candidates.size() == 0) {
			noResultException("Unable to find any free volume for LUN allocation.");
		}
		return candidates;
	}

	private void remember(String volumeName) {
		volumeDateChosen.put(volumeName, System.nanoTime());
	}

	private void checkValidRequest(String[] volumeNames) {
		if (volumeNames == null || volumeNames.length == 0) {
			noResultException("Empty list of volumes provided.");
		}
	}

	private void checkVolumesKnown() {
		if (volumes.isEmpty()) {
			noResultException("Empty volumes");
		}
	}

	private void noResultException(String msg) {
		logger.warning("Current state:" + volumes);
		logger.warning(msg);
		throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
				"Unable to allocate new disk: backend full");
	}

}
