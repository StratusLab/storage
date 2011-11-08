package eu.stratuslab.storage.disk.plugins;

import java.io.File;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.FileUtils;

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
}
