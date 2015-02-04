package eu.stratuslab.storage.disk.backend;

import static eu.stratuslab.storage.disk.backend.VolumeChooserTestHelper.vc;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class VolumeChooserTest {

	@Before
	public void resetVolumes() {
		URL configFile = this.getClass().getResource("/pdisk.test.cfg");
		System.setProperty("pdisk.config.filename", configFile.getFile());
		
		VolumeChooser.getInstance().volumes = new HashMap<String, Integer>();
	}

	@Test(expected = ResourceException.class)
	public void requestVolumeNameWhenNoUpdatesWasDoneShouldFail() throws Exception {
		VolumeChooser.getInstance().requestVolumeName();
	}

	@Test(expected = ResourceException.class)
	public void requestVolumeNameFromWhenNoUpdatesWasDoneShouldFail() throws Exception {
		VolumeChooser.getInstance().requestVolumeNameFrom(new String[]{"v1", "v2"});
	}

	@Test
	public void requestVolumeNameShouldChooseLeastFilled() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2"), Arrays.asList(1, 0));

		assertEquals("v2", vc.requestVolumeName());

		// v1 and v2 should now both contain 1
		int nbChosenV1 = 0;
		int nbChosenV2 = 0;
		for (int i = 0; i < 10; i++) {
			if ("v1".equals(vc.requestVolumeName())) {
				nbChosenV1++;
			} else {
				nbChosenV2++;
			}
		}
		Assert.assertEquals(nbChosenV1, nbChosenV2);
	}

	@Test
	public void requestVolumeNameWithRetryFromShouldChooseLeastFilledInGivenSet() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2", "v3"), Arrays.asList(10, 5, 7));

		String[] v1v3 = new String[]{"v1", "v3"};

		// 10 5 7
		assertEquals("v3", vc.requestVolumeNameWithRetryFrom(v1v3));
		// 10 5 8
		assertEquals("v2", vc.requestVolumeName());
		// 10 6 8
	}

	@Test(expected = ResourceException.class)
	public void requestVolumeNamesFromWhenVolumeNotKnown() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2"), Arrays.asList(3, 4));
		vc.requestVolumeNameFrom(new String[]{"vXXX"});
	}

	@Test
	public void partialUpdateVolumes() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1"), Arrays.asList(0));		
		// 0
		assertEquals("v1", vc.requestVolumeName());
		// 1
				
		vc.updateVolume("v2", 0);
		// 1 0
		assertEquals("v2", vc.requestVolumeName());
		// 1 1
	}
	
	@Test
	public void requestVolumeShouldTakeIntoAccountUpdateVolumes() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2", "v3"), Arrays.asList(10, 5, 7));

		// 10 5 7
		assertEquals("v2", vc.requestVolumeName());
		// 10 6 7 
		assertEquals("v2", vc.requestVolumeName());
		// 10 7 7

		vc.updateVolume("v1", 0);

		// 0 7 7
		assertEquals("v1", vc.requestVolumeName());
		// 1 7 7
	}

	@Test
	public void releaseVolumeShouldActuallyMakePlace() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2"), Arrays.asList(1, 3));
		// 1 3
		vc.releaseVolume("v2");
		vc.releaseVolume("v2");
		vc.releaseVolume("v2");
		// 1 0
		assertEquals("v2", vc.requestVolumeName());
		// 1 1
	}
	
	@Test
	public void unknownReleaseVolumeShouldBeIgnored() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2"), Arrays.asList(1, 3));
		vc.releaseVolume("v1234");
		assertEquals("v1", vc.requestVolumeName());
	}

	@Test
	public void whenFullShouldThrowException() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1"), Arrays.asList(0));
		// 0
		for (int i = 0; i < vc.maxLUN; i++) {
			assertEquals("v1", vc.requestVolumeName());
		}
		// v1 is now full
		try {
			vc.requestVolumeName();
			fail("should have raised an exception");
		} catch (ResourceException iaeIgnore) {
		}
	}

	@Test
	public void showPercentage() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2", "v3"), Arrays.asList(50, 70, 65));		
		assertEquals((double)(50+70+65)/(3*vc.maxLUN), vc.percentageConsumed(), 1e-6);		
	}

}
