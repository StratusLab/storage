package eu.stratuslab.storage.disk.plugins;

public interface DiskSharing {

    public void preDiskCreationActions(String uuid);

    public void postDiskCreationActions(String uuid);

    public void preDiskRemovalActions(String uuid);

    public void postDiskRemovalActions(String uuid);
}
