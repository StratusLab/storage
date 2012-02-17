package eu.stratuslab.storage.persistent;

import org.junit.Test;

import eu.stratuslab.storage.persistence.Disk;


public class DiskTest {

	@Test
	public void testStore() {

		Disk disk = new Disk();
		disk.store();
	}
}
