package eu.stratuslab.storage.disk.utils;

import java.io.File;

import eu.stratuslab.storage.disk.main.RootApplication;

public class CompressedDiskRemoval implements Runnable {
	String requestedCompressedDisk;
	File diskToRemove;

	public CompressedDiskRemoval(String uuid) {
		requestedCompressedDisk = uuid + ".gz";
	}

	public void run() {
		logInfo("Finding disk to remove");

		findDiskToRemove();

		if (diskToRemove != null) {
			logInfo("Removing compressed disk "
					+ diskToRemove.getAbsolutePath());
			if (!diskToRemove.delete()) {
				logInfo("Unable to remove disk "
						+ diskToRemove.getAbsolutePath());
			}
		}
	}

	private void findDiskToRemove() {
		File cache = new File(RootApplication.CONFIGURATION.CACHE_LOCATION);
		File testedDisk;
		File copiedDisk;

		for (String currentDisk : cache.list()) {
			if (currentDisk.endsWith(".gz")) {
				copiedDisk = new File(
						RootApplication.CONFIGURATION.CACHE_LOCATION,
						currentDisk.replaceAll(".gz", ""));
				testedDisk = new File(
						RootApplication.CONFIGURATION.CACHE_LOCATION,
						currentDisk);
				
				if (!requestedCompressedDisk.equals(currentDisk)
						&& !copiedDisk.exists()
						&& DiskUtils.hasCompressedDiskExpire(diskToRemove)) {
					diskToRemove = testedDisk;
					break;
				}
			}
		}
	}

	private void logInfo(String msg) {
		RootApplication.getCurrent().getLogger().info(msg);
	}
}
