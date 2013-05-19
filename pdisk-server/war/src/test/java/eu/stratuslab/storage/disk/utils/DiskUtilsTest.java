package eu.stratuslab.storage.disk.utils;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class DiskUtilsTest {

    @Test
    public void testCalculateImageIdentifier() throws IOException {

        InputStream image = new FileInputStream(this.getClass()
                .getResource("/test.img").getFile());

        String identifier = DiskUtils.calculateHash(image);

        assertThat(identifier, is("Nyuwnr8SOXWilAOWhWTELpytQKS"));
    }

    @Test
    public void testConvertBytesToGibiBytes() {
        assertThat(DiskUtils.convertBytesToGibiBytes(-1L), is((long) 1));

        assertThat(DiskUtils.convertBytesToGibiBytes(0L), is((long) 1));

        long oneGBofBytes = 1024 * 1024 * 1024L;
        long twoGBofBytes = 2 * 1024 * 1024 * 1024L;

        assertThat(DiskUtils.convertBytesToGibiBytes(oneGBofBytes - 1),
                is((long) 1));
        assertThat(DiskUtils.convertBytesToGibiBytes(oneGBofBytes),
                is((long) 1));
        assertThat(DiskUtils.convertBytesToGibiBytes(oneGBofBytes + 1),
                is((long) 2));
        assertThat(DiskUtils.convertBytesToGibiBytes(twoGBofBytes),
                is((long) 2));
        assertThat(DiskUtils.convertBytesToGibiBytes(twoGBofBytes + 1),
                is((long) 3));
    }
}
