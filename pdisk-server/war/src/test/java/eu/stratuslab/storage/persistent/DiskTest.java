package eu.stratuslab.storage.persistent;

import static org.junit.Assert.assertThat;

import org.hamcrest.core.Is;
import org.junit.Test;

import eu.stratuslab.storage.persistence.Disk;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;


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
		
		assertThat(Disk.countSnapshots(origin.getUuid()), is((int)2));
	}
}
