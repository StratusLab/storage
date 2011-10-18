package eu.stratuslab.storage.disk.plugins;

import java.io.File;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.ProcessUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;

public final class LvmStorage implements DiskStorage {

    public LvmStorage() {

    }

    public void create(String uuid, int size) {
        File diskFile = new File(RootApplication.CONFIGURATION.LVM_GROUP_PATH,
                uuid);

        if (diskFile.exists()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "A disk with the same name already exists.");
        }

        String lvmSize = size + "G";
        ProcessBuilder pb = new ProcessBuilder(
                RootApplication.CONFIGURATION.LVCREATE_CMD, "-L", lvmSize,
                RootApplication.CONFIGURATION.LVM_GROUP_PATH, "-n", uuid);

        ProcessUtils.execute(pb, "Unable to recreate the LVM volume");
    }

    public void delete(String uuid) {
        String volumePath = RootApplication.CONFIGURATION.LVM_GROUP_PATH + "/"
                + uuid;

        if (false == (new File(volumePath)).exists()) {
        	return;
        }

        ProcessBuilder lvremove = new ProcessBuilder(
                RootApplication.CONFIGURATION.LVREMOVE_CMD, "-f", volumePath);

        if (ProcessUtils.executeGetStatus(lvremove) != 0) {
        	ProcessBuilder lvchange = new ProcessBuilder(
        			RootApplication.CONFIGURATION.LVCHANGE_CMD,
        			"-a n", volumePath);
        	ProcessUtils.executeGetStatus(lvchange);

        	if (ProcessUtils.executeGetStatus(lvremove) != 0) {
        		// dmsetup needs as input <VG name>-<UUID> sanitized with 's/-/--/g;s|/|-|g'
        		String volumeGroupName = MiscUtils.sub("^.*/", "", RootApplication.CONFIGURATION.LVM_GROUP_PATH)
        				.replaceAll("/", "");
        		String uuidDm = uuid.replaceAll("-", "--");
        		String volumePathDm = volumeGroupName + "-" + uuidDm;

        		ProcessBuilder dmsetup = new ProcessBuilder(
        				RootApplication.CONFIGURATION.DMSETUP_CMD,
        				"remove", volumePathDm);
        		ProcessUtils.executeGetStatus(dmsetup);

		        ProcessUtils.execute(lvremove, "It's possible that the disk " + uuid
		        		+ " is still logged on a node");
        	}
        }
    }
}
