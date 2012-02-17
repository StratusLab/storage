package eu.stratuslab.storage.disk.plugins;

import java.util.List;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.DiskView;

public final class IscsiSharing implements DiskSharing {

	// Template for an iSCSI target entry.
	private static final String TARGET_TEMPLATE = "<target iqn.2011-01.eu.stratuslab:%s>\n"
			+ "backing-store %s/%s\n" + "</target>\n";

	public IscsiSharing() {

	}

	public void preDiskCreationActions() {

	}

	public void postDiskCreationActions() {
		updateISCSIConfiguration();
	}

	public void preDiskRemovalActions() {
		updateISCSIConfiguration();
	}

	public void postDiskRemovalActions() {

	}

	private static Boolean updateISCSIConfiguration() {
		String configuration = createISCSITargetConfiguration();

		FileUtils.writeToFile(RootApplication.CONFIGURATION.ISCSI_CONFIG,
				configuration);

		updateISCSIServer();

		return true;
	}

	private static String createISCSITargetConfiguration() {
		StringBuilder sb = new StringBuilder();
		List<DiskView> disks = getAllDisks();
		String disksLocation = getDisksLocation();

		for (DiskView disk : disks) {
			sb.append(String.format(TARGET_TEMPLATE, disk.getUuid(),
					disksLocation, disk.getUuid()));
		}

		return sb.toString();
	}

	private static void updateISCSIServer() {
		ProcessBuilder pb = new ProcessBuilder(
				RootApplication.CONFIGURATION.ISCSI_ADMIN, "--update", "ALL");

		ProcessUtils.execute(pb, "Perhaps there is a syntax error in "
				+ RootApplication.CONFIGURATION.ISCSI_CONFIG.getAbsolutePath()
				+ " or in "
				+ ServiceConfiguration.DEFAULT_ISCSI_CONFIG_FILENAME);
	}

	private static List<DiskView> getAllDisks() {
		return Disk.viewListAll();
	}

	private static String getDisksLocation() {
		if (RootApplication.CONFIGURATION.ISCSI_DISK_TYPE == ServiceConfiguration.DiskType.FILE) {
			return RootApplication.CONFIGURATION.STORAGE_LOCATION
					.getAbsolutePath();
		} else {
			return RootApplication.CONFIGURATION.LVM_GROUP_PATH;
		}
	}
}
