package eu.stratuslab.storage.disk.plugins;

public interface DiskSharing {

    public void preDiskCreationActions();

    public void postDiskCreationActions();

    public void preDiskRemovalActions();

    public void postDiskRemovalActions();
    
    public void removeDiskSharing(String uuid);
}
