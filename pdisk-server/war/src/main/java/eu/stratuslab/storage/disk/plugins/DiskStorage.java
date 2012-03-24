package eu.stratuslab.storage.disk.plugins;

public interface DiskStorage {

    public void create(String uuid, long size);

    public void createCopyOnWrite(String baseUuid, String cowUuid, long size);

    public void rebase(String uuid, String rebaseUuid);

    public void delete(String uuid);
    
    public String getDiskLocation(String uuid);

}
