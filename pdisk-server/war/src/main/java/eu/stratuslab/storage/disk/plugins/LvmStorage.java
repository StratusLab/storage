package eu.stratuslab.storage.disk.plugins;

import java.io.File;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.ProcessUtils;

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
        ProcessBuilder pb = new ProcessBuilder(
                RootApplication.CONFIGURATION.LVREMOVE_CMD, "-f", volumePath);

        ProcessUtils.execute(pb, "It's possible that the disk " + uuid
                + " is still logged on a node");
    }

}
