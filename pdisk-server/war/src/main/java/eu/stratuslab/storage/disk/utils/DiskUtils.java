package eu.stratuslab.storage.disk.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.logging.Logger;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public final class DiskUtils {

	// Template for an iSCSI target entry. Fields passed to the formatter should
	// be the path for the disk store and the uuid.
	private static final String TARGET_TEMPLATE = "Target iqn.2011-01.eu.stratuslab:%2$s\n"
			+ "\tLun 0 Path=%1$s/%2$s,Type=fileio\n\n";

	private static final Logger LOGGER = Logger.getLogger("org.restlet");

	public static String createTargetConfiguration() {
		StringBuilder sb = new StringBuilder();
		List<String> disks = null;
		String storePath;
		String diskSuffix = "";

		try {
			ZooKeeper zk = new ZooKeeper(PersistentDiskApplication.ZK_ADDRESS,
					PersistentDiskApplication.ZK_PORT, null);

			disks = zk.getChildren(PersistentDiskApplication.ZK_ROOT_PATH,
					false);
		} catch (KeeperException e) {
			LOGGER.severe("error retrieving disk list: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error retrieving disk list: " + e.getMessage());
		} catch (IOException e) {
			LOGGER.severe("error retrieving disk list: " + e.getMessage());
		}

		if (PersistentDiskApplication.DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			storePath = PersistentDiskApplication.DISK_STORE.getName();
			diskSuffix = "/contents";
		} else {
			storePath = PersistentDiskApplication.LVM_GROUPE_PATH;
		}

		if (disks == null) {
			return "";
		}

		for (String uuid : disks) {
			sb.append(String.format(TARGET_TEMPLATE, storePath, uuid
					+ diskSuffix));
		}

		return sb.toString();
	}

	public static void writeStringToFile(String contents, File file)
			throws IOException {

		Writer writer = null;

		try {

			writer = new FileWriter(file);
			writer.append(contents);

		} catch (IOException e) {
			throw e;
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException consumed) {
					LOGGER.severe("error closing file (" + file + "): "
							+ consumed.getMessage());
				}
			}
		}
	}

	public static void zeroFile(File file, int sizeInGB) throws IOException {

		OutputStream ostream = null;

		try {

			ostream = new FileOutputStream(file);

			// Create 1 MB buffer of zeros.
			byte[] buffer = new byte[1024000];

			for (int i = 0; i < 1000 * sizeInGB; i++) {
				ostream.write(buffer);
			}

		} catch (IOException e) {
			throw e;
		} finally {
			if (ostream != null) {
				try {
					ostream.close();
				} catch (IOException consumed) {
					LOGGER.severe("error closing file (" + file + "): "
							+ consumed.getMessage());
				}
			}
		}

	}

	public static void restartServer() throws IOException {

		File cfgFile = new File("/etc/iet/ietd.conf");
		File initFile = new File("/etc/init.d/iscsi-target");

		if (cfgFile.canWrite()) {

			String contents = DiskUtils.createTargetConfiguration();
			DiskUtils.writeStringToFile(contents, cfgFile);

			if (initFile.canExecute()) {
				ProcessBuilder pb = new ProcessBuilder(
						initFile.getAbsolutePath(), "restart");
				Process process = pb.start();

				boolean blocked = true;
				while (blocked) {
					try {
						process.waitFor();
						blocked = false;
					} catch (InterruptedException consumed) {
						// Just continue with the loop.
					}
				}
				int rc = process.exitValue();

				if (rc != 0) {
					LOGGER.severe("iscsi-target restart failed: " + rc);
				}
			} else {
				LOGGER.severe("cannot run iscsi-target script");
			}

		} else {
			LOGGER.severe("cannot write to iet.conf file");
		}

	}

}
