package eu.stratuslab.storage.disk.backend;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class BackendCheckerTest {

	@Before
	public void resetVolumeChooser() {
		URL configFile = this.getClass().getResource("/pdisk.test.cfg");
		System.setProperty("pdisk.config.filename", configFile.getFile());

		VolumeChooser.getInstance().volumes = new HashMap<String, Integer>();
	}

	private class BackEndStorageMock extends BackEndStorage {

		private Map<String, Integer> mockData = new HashMap<String, Integer>();

		public BackEndStorageMock(List<String> volumes, List<Integer> values) {
			for (int i = 0; i < volumes.size(); i++) {
				mockData.put(volumes.get(i), values.get(i));
			}
		}

		@Override
		public Integer getNumberOfMappedLuns(String volume) {
			return mockData.get(volume);
		}
	}

	@Test(expected = ResourceException.class)
	public void testBackendCheckerEmpty() {

		final List<String> volumes = Arrays.asList();
		final List<Integer> values = Arrays.asList();
		mockBackend(volumes, values);

		VolumeChooser.getInstance().requestVolumeName();
	}

	@Test
	public void testBackendCheckerWithOneVolume() {

		final List<String> volumes = Arrays.asList("v1");
		final List<Integer> values = Arrays.asList(1);
		mockBackend(volumes, values);

		Assert.assertEquals("v1", VolumeChooser.getInstance()
				.requestVolumeName());
	}

	@Test
	public void leastFilledVolumeIsReturned() throws Exception {

		final List<String> volumes = Arrays.asList("v10", "v20");
		final List<Integer> values = Arrays.asList(3, 1);
		mockBackend(volumes, values);

		Assert.assertEquals("v20", VolumeChooser.getInstance()
				.requestVolumeName());
	}

	private void mockBackend(final List<String> volumes,
			final List<Integer> values) {
		BackEndStorage backEnd = new BackEndStorageMock(volumes, values);

		new BackendChecker(backEnd) {
			protected String[] getBackendProxiesFromConfig() {
				return volumes.toArray(new String[0]);
			}
		};		
	}

}
