package eu.stratuslab.storage.disk.plugins;

public interface DiskStorage {

    public void create(String uuid, int size);

    public void createCopyOnWrite(String baseUuid, String cowUuid, int size);

    public String rebase(String uuid);

    public void delete(String uuid);

}
