package eu.stratuslab.storage.disk.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.main.PersistentDiskApplication.ShareType;
import eu.stratuslab.storage.disk.resources.BaseResource;

public final class DiskUtils {

	// Template for an iSCSI target entry.
	private static final String TARGET_TEMPLATE = "<target iqn.2011-01.eu.stratuslab:%s>\n"
			+ "backing-store %s/%s\n" + "</target>\n";

	private static void preDiskCreationActions() {

	}

	private static void postDiskCreationActions() {
		if (PersistentDiskApplication.SHARE_TYPE == ShareType.ISCSI) {
			updateISCSIConfiguration();
		}
	}

	public static void createDisk(Properties properties) {
		String uuid = properties.getProperty(DiskProperties.UUID_KEY)
				.toString();
		int size = Integer.parseInt(properties.getProperty("size").toString());

		preDiskCreationActions();

		if (PersistentDiskApplication.SHARE_TYPE == ShareType.NFS
				|| PersistentDiskApplication.ISCSI_DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			createFileDisk(uuid, size);
		} else {
			createLVMDisk(uuid, size);
		}

		postDiskCreationActions();
	}

	private static void createFileDisk(String uuid, int size) {
		File diskFile = new File(PersistentDiskApplication.STORAGE_LOCATION,
				uuid);

		if (diskFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"A disk with the same name already exists.");
		}

		FileUtils.createZeroFile(diskFile, size);
	}

	private static void createLVMDisk(String uuid, int size) {
		File diskFile = new File(PersistentDiskApplication.LVM_GROUPE_PATH,
				uuid);

		if (diskFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"A disk with the same name already exists.");
		}

		String lvmSize = size + "G";
		ProcessBuilder pb = new ProcessBuilder(
				PersistentDiskApplication.LVCREATE_CMD, "-L", lvmSize,
				PersistentDiskApplication.LVM_GROUPE_PATH, "-n", uuid);

		ProcessUtils.execute(pb, "Unable to recreate the LVM volume");
	}

	public static void preDiskRemovalActions() {
		if (PersistentDiskApplication.SHARE_TYPE == ShareType.ISCSI) {
			updateISCSIConfiguration();
		}
	}

	public static void postDiskRemovalActions() {

	}

	public static void removeDisk(String uuid) {
		preDiskRemovalActions();

		if (PersistentDiskApplication.SHARE_TYPE == ShareType.NFS
				|| PersistentDiskApplication.ISCSI_DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			removeFileDisk(uuid);
		} else {
			removeLVMDisk(uuid);
		}

		postDiskRemovalActions();
	}

	public static void attachHotplugDisk(String serviceName, int servicePort,
			String node, String vmId, String diskUuid, String target) {

		String attachedDisk = PersistentDiskApplication.CLOUD_NODE_VM_DIR + "/"
				+ vmId + "/images/pdisk-" + diskUuid;

		List<String> attachCmd = new ArrayList<String>();
		attachCmd.add("ssh");
		attachCmd.add("-p");
		attachCmd.add("22");
		attachCmd.add("-o");
		attachCmd.add("ConnectTimeout=5");
		attachCmd.add("-o");
		attachCmd.add("StrictHostKeyChecking=no");
		attachCmd.add("-i");
		attachCmd.add(PersistentDiskApplication.CLOUD_NODE_SSH_KEY);
		attachCmd.add(PersistentDiskApplication.CLOUD_NODE_ADMIN + "@" + node);
		attachCmd.add("/usr/sbin/attach-persistent-disk.sh");
		attachCmd.add("pdisk:" + serviceName + ":"
				+ String.valueOf(servicePort) + ":" + diskUuid);
		attachCmd.add(attachedDisk);
		attachCmd.add(target);

		ProcessBuilder pb = new ProcessBuilder(attachCmd);
		ProcessUtils.execute(pb, "Unable to attach persistent disk");
	}

	public static void detachHotplugDisk(String serviceName, int servicePort,
			String node, String vmId, String diskUuid, String target) {

		List<String> detachCmd = new ArrayList<String>();
		detachCmd.add("ssh");
		detachCmd.add("-p");
		detachCmd.add("22");
		detachCmd.add("-o");
		detachCmd.add("ConnectTimeout=5");
		detachCmd.add("-o");
		detachCmd.add("StrictHostKeyChecking=no");
		detachCmd.add("-i");
		detachCmd.add(PersistentDiskApplication.CLOUD_NODE_SSH_KEY);
		detachCmd.add(PersistentDiskApplication.CLOUD_NODE_ADMIN + "@" + node);
		detachCmd.add("/usr/sbin/detach-persistent-disk.sh");
		detachCmd.add("pdisk:" + serviceName + ":"
				+ String.valueOf(servicePort) + ":" + diskUuid);
		detachCmd.add(target);
		detachCmd.add(vmId);

		ProcessBuilder pb = new ProcessBuilder(detachCmd);
		ProcessUtils.execute(pb, "Unable to detach persistent disk");
	}

	private static void removeFileDisk(String uuid) {
		File diskFile = new File(PersistentDiskApplication.STORAGE_LOCATION,
				uuid);

		if (!diskFile.delete()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"An error occcured while removing disk content " + uuid);
		}
	}

	private static void removeLVMDisk(String uuid) {
		String volumePath = PersistentDiskApplication.LVM_GROUPE_PATH + "/"
				+ uuid;
		ProcessBuilder pb = new ProcessBuilder(
				PersistentDiskApplication.LVREMOVE_CMD, "-f", volumePath);

		ProcessUtils.execute(pb, "It's possible that the disk " + uuid
				+ " is still logged on a node");
	}

	private static Boolean updateISCSIConfiguration() {
		String configuration = createISCSITargetConfiguration();

		FileUtils.writeToFile(PersistentDiskApplication.ISCSI_CONFIG,
				configuration);

		updateISCSIServer();

		return true;
	}

	private static String createISCSITargetConfiguration() {
		StringBuilder sb = new StringBuilder();
		List<String> disks = getAllDisks();
		String disksLocation = getDisksLocation();

		for (String uuid : disks) {
			sb.append(String.format(TARGET_TEMPLATE, uuid, disksLocation, uuid));
		}

		return sb.toString();
	}

	private static void updateISCSIServer() {
		ProcessBuilder pb = new ProcessBuilder(
				PersistentDiskApplication.ISCSI_ADMIN, "--update", "ALL");

		ProcessUtils.execute(pb, "Perhaps there is a syntax error in "
				+ PersistentDiskApplication.ISCSI_CONFIG.getAbsolutePath()
				+ " or in " + PersistentDiskApplication.ISCSI_CONFIG_FILENAME);
	}

	private static List<String> getAllDisks() {
		DiskProperties zk = BaseResource.getZooKeeper();

		return zk.getDisks();
	}

	private static String getDisksLocation() {
		if (PersistentDiskApplication.ISCSI_DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			return PersistentDiskApplication.STORAGE_LOCATION.getAbsolutePath();
		} else {
			return PersistentDiskApplication.LVM_GROUPE_PATH;
		}
	}
}
