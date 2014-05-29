package eu.stratuslab.storage.disk.backend;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.ProcessUtils;
import eu.stratuslab.storage.persistence.Disk;

import java.util.ArrayList;
import java.util.List;

public final class BackEndStorage {

    private String CONFIG = "/etc/stratuslab/pdisk-backend.cfg";
    private String CMD = "/usr/sbin/persistent-disk-backend.py";

    public void create(String uuid, long size) {
        String errorMsg = "Unable to create volume on backend storage: " + uuid + " of size " + size;

        String[] args = {uuid, String.valueOf(size)};
        execute("create", errorMsg, args);
    }

    protected String execute(String action, String errorMsg, String... arguments) {
        String[] preArgs = {CMD, "--config", CONFIG, "--action", action};
        List<String> args = new ArrayList<String>();
        for (String s : preArgs) {
            args.add(s);
        }
        for (String s : arguments) {
            args.add(s);
        }

        return execute(errorMsg, args.toArray(new String[args.size()]));
    }

    private String execute(String errorMsg, String... arguments) {
        ProcessBuilder pb = new ProcessBuilder(arguments);

        return ProcessUtils.executeWithOutput(pb, errorMsg);
    }

    // TODO: expose this
    protected String checkDiskExists(String baseUuid) {

        String[] args = {baseUuid};
        return execute("check", "Volume does not exist on backend storage: " + baseUuid, args);
    }

    public String getTurl(String baseUuid) {

        String[] args = {baseUuid};
        return execute("getturl", "Cannot find transport URL (turl) for uuid: " + baseUuid, args).trim();
    }

    public String rebase(Disk disk) {

        String[] args = {disk.getUuid()};
        String errorMsg = "Cannot rebase image on backend storage: " + disk.getUuid();
        String rebasedUuid = execute("rebase", errorMsg, args);

        return rebasedUuid;
    }

    public String createCopyOnWrite(String baseUuid, String cowUuid, long size) {

        String[] args = {baseUuid, cowUuid, Long.toString(size)};
        String errorMsg = "Cannot create copy on write volume: " + baseUuid + " " + cowUuid + " " + size;
        return execute("snapshot", errorMsg, args);
    }

    public void delete(String uuid) {

        String[] args = {uuid, "0"};
        String errorMsg = "Unable to delete volume on backend storage: " + uuid;

        execute("delete", errorMsg, args);
    }

    public String getDiskLocation(String vmId, String diskUuid) {
        String attachedDisk = RootApplication.CONFIGURATION.CLOUD_NODE_VM_DIR + "/" + vmId + "/images/pdisk-" +
                diskUuid;
        return attachedDisk;
    }

    public void map(String uuid) {
        String[] args = {uuid};

        execute("map", "Unable to map: " + uuid, args);
    }

    public void unmap(String uuid) {
        String[] args = {uuid};

        execute("unmap", "Unable to unmap: " + uuid, args);
    }

}
