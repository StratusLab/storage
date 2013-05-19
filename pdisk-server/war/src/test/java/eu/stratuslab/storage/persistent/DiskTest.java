package eu.stratuslab.storage.persistent;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import eu.stratuslab.storage.persistence.Disk;

public class DiskTest {

    @Test
    public void testStore() {

        Disk disk = new Disk();
        disk.store();
    }

    @Test
    public void testCountByIdentifierQuery() {
        Disk origin = new Disk();
        origin.store();

        Disk snapshot;
        snapshot = new Disk();
        snapshot.setIdentifier("snapshot:" + origin.getUuid());
        snapshot.store();
        snapshot = new Disk();
        snapshot.setIdentifier("snapshot:" + origin.getUuid());
        snapshot.store();

        assertThat(Disk.countSnapshots(origin.getUuid()), is((int) 2));
    }
}
