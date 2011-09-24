package eu.stratuslab.storage.disk.plugins;

public interface DiskStorage {

    public void create(String uuid, int size);

    public void delete(String uuid);

}
