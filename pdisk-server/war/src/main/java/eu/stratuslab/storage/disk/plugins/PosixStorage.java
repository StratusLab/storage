package eu.stratuslab.storage.disk.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;

public final class PosixStorage implements DiskStorage {

	public PosixStorage() {

	}

	public void create(String uuid, int size) {
		File diskFile = new File(
				RootApplication.CONFIGURATION.STORAGE_LOCATION, uuid);

		if (diskFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"A disk with the same name already exists.");
		}

		FileUtils.createZeroFile(diskFile, size);
	}

	public void delete(String uuid) {
		File diskFile = new File(
				RootApplication.CONFIGURATION.STORAGE_LOCATION, uuid);

		if (!diskFile.delete()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"An error occcured while removing disk content " + uuid);
		}
	}

	public void createCopyOnWrite(String baseUuid, String cowUuid, int size) {
		// TODO Auto-generated method stub
	}

	public String rebase(String uuid, String rebaseUuid) {
		// TODO Auto-generated method stub
		return null;
	}

	public String zip(String uuid) {
		File disk = new File(
				RootApplication.CONFIGURATION.STORAGE_LOCATION
						.getAbsolutePath() + "/" + uuid);

		if (!disk.isFile()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Disk "
					+ uuid + " does not exists");
		}

		String cachedDisk = RootApplication.CONFIGURATION.CACHE_LOCATION + "/"
				+ uuid;

		FileUtils.copyFile(
				RootApplication.CONFIGURATION.STORAGE_LOCATION
						.getAbsolutePath() + "/" + uuid, cachedDisk);

		List<String> zipCmd = new ArrayList<String>();
		zipCmd.add("gzip");
		zipCmd.add("-f");
		zipCmd.add(cachedDisk);

		ProcessBuilder pb = new ProcessBuilder(zipCmd);
		ProcessUtils.execute(pb, "Unable to attach persistent disk");

		return cachedDisk + ".gz";
	}
	
}
