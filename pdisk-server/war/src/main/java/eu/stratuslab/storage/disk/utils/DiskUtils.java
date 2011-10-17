package eu.stratuslab.storage.disk.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.main.ServiceConfiguration.ShareType;
import eu.stratuslab.storage.disk.plugins.DiskSharing;
import eu.stratuslab.storage.disk.plugins.DiskStorage;
import eu.stratuslab.storage.disk.plugins.FileSystemSharing;
import eu.stratuslab.storage.disk.plugins.IscsiSharing;
import eu.stratuslab.storage.disk.plugins.LvmStorage;
import eu.stratuslab.storage.disk.plugins.PosixStorage;

public final class DiskUtils {

	private DiskUtils() {

	}

	private static DiskSharing getDiskSharing() {
		switch (RootApplication.CONFIGURATION.SHARE_TYPE) {
		case NFS:
			return new FileSystemSharing();
		case ISCSI:
			return new IscsiSharing();
		default:
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL);
		}
	}

	private static DiskStorage getDiskStorage() {

		if (RootApplication.CONFIGURATION.SHARE_TYPE == ShareType.NFS
				|| RootApplication.CONFIGURATION.ISCSI_DISK_TYPE == ServiceConfiguration.DiskType.FILE) {

			return new PosixStorage();
		} else {
			return new LvmStorage();
		}
	}

	public static void createDisk(Properties properties) {
		String uuid = properties.getProperty(DiskProperties.UUID_KEY)
				.toString();

		DiskSharing diskSharing = getDiskSharing();
		DiskStorage diskStorage = getDiskStorage();

		diskSharing.preDiskCreationActions();

		diskStorage.create(uuid, getSize(properties));

		diskSharing.postDiskCreationActions();
	}

	public static String createCoWDisk(Properties properties) {
		String uuid = properties.getProperty(DiskProperties.UUID_KEY)
				.toString();

		DiskSharing diskSharing = getDiskSharing();
		DiskStorage diskStorage = getDiskStorage();

		diskSharing.preDiskCreationActions();

		String cowUuid = generateUUID();
			
		diskStorage.createCopyOnWrite(uuid, cowUuid, getSize(properties));

		diskSharing.postDiskCreationActions();
		
		return cowUuid;
	}

	public static void rebaseDisk(Properties properties) {
		String uuid = properties.getProperty(DiskProperties.UUID_KEY)
				.toString();

		DiskSharing diskSharing = getDiskSharing();
		DiskStorage diskStorage = getDiskStorage();

		diskSharing.preDiskRemovalActions();

		diskStorage.rebase(uuid);

		diskSharing.postDiskRemovalActions();
	}

	protected static int getSize(Properties properties) {
		return Integer.parseInt(properties.getProperty("size"));
	}

	public static void removeDisk(String uuid) {

		DiskSharing diskSharing = getDiskSharing();
		DiskStorage diskStorage = getDiskStorage();

		diskSharing.preDiskRemovalActions();

		diskStorage.delete(uuid);

		diskSharing.postDiskRemovalActions();
	}

	public static void attachHotplugDisk(String serviceName, int servicePort,
			String node, String vmId, String diskUuid, String target) {

		String attachedDisk = RootApplication.CONFIGURATION.CLOUD_NODE_VM_DIR
				+ "/" + vmId + "/images/pdisk-" + diskUuid;

		List<String> attachCmd = new ArrayList<String>();
		attachCmd.add("ssh");
		attachCmd.add("-p");
		attachCmd.add("22");
		attachCmd.add("-o");
		attachCmd.add("ConnectTimeout=5");
		attachCmd.add("-o");
		attachCmd.add("StrictHostKeyChecking=no");
		attachCmd.add("-i");
		attachCmd.add(RootApplication.CONFIGURATION.CLOUD_NODE_SSH_KEY);
		attachCmd.add(RootApplication.CONFIGURATION.CLOUD_NODE_ADMIN + "@"
				+ node);
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
		detachCmd.add(RootApplication.CONFIGURATION.CLOUD_NODE_SSH_KEY);
		detachCmd.add(RootApplication.CONFIGURATION.CLOUD_NODE_ADMIN + "@"
				+ node);
		detachCmd.add("/usr/sbin/detach-persistent-disk.sh");
		detachCmd.add("pdisk:" + serviceName + ":"
				+ String.valueOf(servicePort) + ":" + diskUuid);
		detachCmd.add(target);
		detachCmd.add(vmId);

		ProcessBuilder pb = new ProcessBuilder(detachCmd);
		ProcessUtils.execute(pb, "Unable to detach persistent disk");
	}

	public static String generateUUID() {
	    return UUID.randomUUID().toString();
	}
}
