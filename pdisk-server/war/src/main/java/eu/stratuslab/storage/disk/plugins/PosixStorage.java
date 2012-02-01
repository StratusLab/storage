package eu.stratuslab.storage.disk.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
		String cachedDisk = RootApplication.CONFIGURATION.CACHE_LOCATION + "/"
				+ uuid;

		copyfile(
				RootApplication.CONFIGURATION.STORAGE_LOCATION.getAbsolutePath()
						+ "/" + uuid, cachedDisk);

		List<String> zipCmd = new ArrayList<String>();
		zipCmd.add("gzip");
		zipCmd.add("-f");
		zipCmd.add(cachedDisk);

		ProcessBuilder pb = new ProcessBuilder(zipCmd);
		ProcessUtils.execute(pb, "Unable to attach persistent disk");

		return cachedDisk + ".gz";
	}

	private static void copyfile(String srFile, String dtFile) {
		try {
			File src = new File(srFile);
			File dst = new File(dtFile);

			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dst);

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}

			in.close();
			out.close();
		} catch (FileNotFoundException ex) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Error while copying disk");
		} catch (IOException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Error while copying disk");
		}
	}
}
