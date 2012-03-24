package eu.stratuslab.storage.disk.plugins;

import eu.stratuslab.storage.persistence.Disk;

public interface DiskStorage {

    public void create(String uuid, long size);

    public void createCopyOnWrite(String baseUuid, String cowUuid, long size);

    public String rebase(Disk disk);

    public void delete(String uuid);
    
    public String getDiskLocation(String uuid);

}
