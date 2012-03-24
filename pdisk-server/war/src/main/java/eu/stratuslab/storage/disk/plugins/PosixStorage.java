package eu.stratuslab.storage.disk.plugins;

import java.io.File;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.persistence.Disk;

public final class PosixStorage implements DiskStorage {

	public PosixStorage() {

	}

	public void create(String uuid, long size) {
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

	public void createCopyOnWrite(String baseUuid, String cowUuid, long size) {
	}

	public String rebase(Disk disk) {
		return null;
	}

	public String getDiskLocation(String uuid) {
		return RootApplication.CONFIGURATION.STORAGE_LOCATION + "/" + uuid;
	}

}
