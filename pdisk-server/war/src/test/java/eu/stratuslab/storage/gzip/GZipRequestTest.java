package eu.stratuslab.storage.gzip;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.junit.Before;
import org.junit.Test;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;

public class GZipRequestTest {

    String uuid = "junitTest";

    @Before
    public void setupConfigFileLocation() {
        URL configFile = this.getClass().getResource("/pdisk.test.cfg");
        System.setProperty("pdisk.config.filename", configFile.getFile());

        System.setProperty("iscsi.config.filename", "/dev/null");
    }

    @Test
    public void testImageBuldingDetection() throws IOException {

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
        File fakeImage = FileUtils.getCachedDiskFile(uuid);
        fakeImage.createNewFile();
    }

    private void createFakeCompressedImageFile(String uuid) throws IOException {
        String fakeCompressedImagePath = FileUtils
                .getCompressedDiskLocation(uuid);
        File fakeCompressedImage = new File(fakeCompressedImagePath);
        fakeCompressedImage.createNewFile();
    }

    private void removeFakeImageFile(String uuid) {
        File fakeImage = FileUtils.getCachedDiskFile(uuid);
        fakeImage.delete();
    }

    private void removeCompressedFakeImageFile(String uuid) {
        String fakeCompressedImagePath = FileUtils
                .getCompressedDiskLocation(uuid);
        File fakeCompressedImage = new File(fakeCompressedImagePath);
        fakeCompressedImage.delete();
    }

    @Test
    public void testCacheExpiration() throws IOException {

        createFakeCompressedImageFile(uuid);

        if (DiskUtils.hasCompressedDiskExpire(uuid)) {
            fail("Image should not have expire yet");
        }

        waitCacheToExpire();

        if (!DiskUtils.hasCompressedDiskExpire(uuid)) {
            fail("Image should have expire");
        }

        removeCompressedFakeImageFile(uuid);
    }

    private void waitCacheToExpire() {
        MiscUtils.sleep(2 * ServiceConfiguration.CACHE_EXPIRATION_DURATION);
    }
}
