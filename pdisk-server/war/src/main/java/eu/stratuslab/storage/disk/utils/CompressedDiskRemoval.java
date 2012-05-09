package eu.stratuslab.storage.disk.utils;

import java.io.File;

import eu.stratuslab.storage.disk.main.RootApplication;

public class CompressedDiskRemoval {
	private String requestedCompressedDisk;

	public CompressedDiskRemoval(String uuid) {
		requestedCompressedDisk = uuid + ".gz";
	}

	public void remove() {
		logInfo("Finding disk to remove");

		File diskToRemove = findDiskToRemove();

		if (diskToRemove != null) {
			logInfo("Removing compressed disk "
					+ diskToRemove.getAbsolutePath());
			if (!diskToRemove.delete()) {
				logInfo("Unable to remove disk "
						+ diskToRemove.getAbsolutePath());
			}
		}
	}

	private File findDiskToRemove() {
		File cacheDirectory = new File(RootApplication.CONFIGURATION.CACHE_LOCATION);
		File testedDisk;
		File copiedDisk;
		File diskToRemove = null;

		for (String currentDisk : cacheDirectory.list()) {
			if (currentDisk.endsWith(".gz")) {
				copiedDisk = new File(
						RootApplication.CONFIGURATION.CACHE_LOCATION,
						currentDisk.replaceAll(".gz", ""));
				testedDisk = new File(
						RootApplication.CONFIGURATION.CACHE_LOCATION,
						currentDisk);
				
				if (!requestedCompressedDisk.equals(currentDisk)
						&& !copiedDisk.exists()
						&& DiskUtils.hasCompressedDiskExpire(testedDisk)) {
					diskToRemove = testedDisk;
					break;
				}
			}
		}
		return diskToRemove;
	}

	private void logInfo(String msg) {
		RootApplication.getCurrent().getLogger().info(msg);
	}
}
