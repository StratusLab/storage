package eu.stratuslab.storage.disk.plugins;

import java.util.List;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;

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
        List<String> disks = getAllDisks();
        String disksLocation = getDisksLocation();

        for (String uuid : disks) {
            sb.append(String.format(TARGET_TEMPLATE, uuid, disksLocation, uuid));
        }

        return sb.toString();
    }

    private static void updateISCSIServer() {
        ProcessBuilder pb = new ProcessBuilder(
                RootApplication.CONFIGURATION.ISCSI_ADMIN, "--update", "ALL");

        ProcessUtils.execute(pb, "Perhaps there is a syntax error in "
                + RootApplication.CONFIGURATION.ISCSI_CONFIG.getAbsolutePath()
                + " or in " + ServiceConfiguration.ISCSI_CONFIG_FILENAME);
    }

    private static List<String> getAllDisks() {
        DiskProperties zk = new DiskProperties();
        return zk.getDisks();
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
