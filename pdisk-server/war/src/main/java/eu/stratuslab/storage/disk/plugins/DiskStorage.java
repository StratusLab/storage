package eu.stratuslab.storage.disk.plugins;

public interface DiskStorage {

    public void create(String uuid, int size);

    public void createCopyOnWrite(String baseUuid, String cowUuid, int size);

    public String rebase(String uuid, String rebaseUuid);

    public void delete(String uuid);
    
    public String getDiskLocation(String uuid);

}
