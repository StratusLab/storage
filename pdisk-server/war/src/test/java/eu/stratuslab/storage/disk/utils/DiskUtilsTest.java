package eu.stratuslab.storage.disk.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;


public class DiskUtilsTest {

	@Test
	public void testCalculateImageIdentifier() throws IOException {

		InputStream image = new FileInputStream(this.getClass().getResource("/test.img").getFile());

		String identifier = DiskUtils.calculateHash(image);

		assertThat(identifier, is("Nyuwnr8SOXWilAOWhWTELpytQKS"));
	}
}
