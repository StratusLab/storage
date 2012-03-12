package eu.stratuslab.storage.disk.plugins;

import java.util.List;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.DiskView;

public final class IscsiSharing implements DiskSharing {

    // Template for an iSCSI target name.
    private static final String TARGET_NAME_TEMPLATE = "iqn.2011-01.eu.stratuslab:%s";

    // Template for an iSCSI target entry.
    private static final String TARGET_TEMPLATE = "<target " + TARGET_NAME_TEMPLATE + ">\n"
            + "backing-store %s/%s\n" + "</target>\n";

	public IscsiSharing() {

	}

    public void preDiskCreationActions(String uuid) {

	}

    public void postDiskCreationActions(String uuid) {
        updateISCSIConfiguration(uuid);
    }

    public void preDiskRemovalActions(String uuid) {
        updateISCSIConfiguration(uuid);
    }

    public void postDiskRemovalActions(String uuid) {

	}

    private static Boolean updateISCSIConfiguration(String uuid) {
        updateIscsiConfigurationFile();

        updateISCSIServer(uuid);

		return true;
	}

	private synchronized static void updateIscsiConfigurationFile() {
		String configuration = createISCSITargetConfiguration(getAllDisks());

        FileUtils.writeToFile(RootApplication.CONFIGURATION.ISCSI_CONFIG,
                configuration);
	}

    private static String createISCSITargetConfiguration(List<DiskView> disks) {
        StringBuilder sb = new StringBuilder();
        String disksLocation = getDisksLocation();

		for (DiskView disk : disks) {
			sb.append(String.format(TARGET_TEMPLATE, disk.getUuid(),
					disksLocation, disk.getUuid()));
		}

		return sb.toString();
	}

    private static void updateISCSIServer(String uuid) {
        ProcessBuilder pb = new ProcessBuilder(
                RootApplication.CONFIGURATION.ISCSI_ADMIN, "--update",
                String.format(TARGET_NAME_TEMPLATE, uuid));

        ProcessUtils.execute(pb, "Perhaps there is a syntax error in "
                + RootApplication.CONFIGURATION.ISCSI_CONFIG.getAbsolutePath()
                + " or in " + ServiceConfiguration.DEFAULT_ISCSI_CONFIG_FILENAME);
    }
    
	private static List<DiskView> getAllDisks() {
		return Disk.listAll();
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
