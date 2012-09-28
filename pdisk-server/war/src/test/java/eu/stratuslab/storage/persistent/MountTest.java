package eu.stratuslab.storage.persistent;

import org.junit.Test;

import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.Instance;
import eu.stratuslab.storage.persistence.Mount;


public class MountTest {

	@Test
	public void testIncrementDiskUserCount() {

		Disk disk = new Disk();
		disk.store();
		Instance instance = new Instance("123", "test");
		instance.store();
		
		Mount mount = new Mount(instance, disk);
		
		mount = mount.store();
		
		mount.remove();
		
		disk.remove();
		instance.remove();
	}
}
