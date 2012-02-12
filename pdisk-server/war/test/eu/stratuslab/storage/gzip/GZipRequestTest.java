package eu.stratuslab.storage.gzip;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.FileUtils;


public class GZipRequestTest {

	@Before
	public void setupConfigFileLocation() {
		// PDisk config have to be valid.
		System.setProperty("pdisk.config.filename", "/tmp/pdisk.cfg");
		System.setProperty("iscsi.config.filename", "/dev/null");
	}
	
	@Test
	public void testImageBuldingDetection() throws IOException {
		String uuid = "junitTest";
		
		createFakeImageFile(uuid);
		
		if (!DiskUtils.isCompressedDiskBuilding(uuid)) {
			fail("Image should be in build state");
		}
		
		createFakeCompressedImageFile(uuid);
		
		if (!DiskUtils.isCompressedDiskBuilding(uuid)) {
			fail("Image should be in build state");
		}
		
		removeFakeImageFile(uuid);
		
		if (DiskUtils.isCompressedDiskBuilding(uuid)) {
			fail("Image should not be in build state");
		}
		
		removeCompressedFakeImageFile(uuid);		
	}
	
	private void createFakeImageFile(String uuid) throws IOException {
		String fakeImagePath = FileUtils.getCachedDiskLocation(uuid);
		File fakeImage = new File(fakeImagePath);
		fakeImage.createNewFile();
	}
	
	private void createFakeCompressedImageFile(String uuid) throws IOException {
		String fakeCompressedImagePath = FileUtils.getCompressedDiskLocation(uuid);
		File fakeCompressedImage = new File(fakeCompressedImagePath);
		fakeCompressedImage.createNewFile();
	}
	
	private void removeFakeImageFile(String uuid) {
		String fakeImagePath = FileUtils.getCachedDiskLocation(uuid);
		File fakeImage = new File(fakeImagePath);
		fakeImage.delete();
	}
	
	private void removeCompressedFakeImageFile(String uuid) {
		String fakeCompressedImagePath = FileUtils.getCachedDiskLocation(uuid);
		File fakeCompressedImage = new File(fakeCompressedImagePath);
		fakeCompressedImage.delete();
	}
}
