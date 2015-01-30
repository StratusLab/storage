package eu.stratuslab.storage.disk.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class VolumeChooserTest {

	@Before
	public void resetVolumes() {
		VolumeChooser.getInstance().volumes = null;
	}

	@Test(expected = IllegalStateException.class)
	public void requestVolumeNameWhenNoUpdatesWasDoneShouldFail() throws Exception {
		VolumeChooser.getInstance().requestVolumeName();
	}

	@Test(expected = IllegalStateException.class)
	public void requestVolumeNameFromWhenNoUpdatesWasDoneShouldFail() throws Exception {
		VolumeChooser.getInstance().requestVolumeNameFrom(new String[]{"v1", "v2"});
	}

	@Test
	public void requestVolumeNameShouldChooseLeastFilled() throws Exception {
		VolumeChooser vc = vc(20, Arrays.asList("v1", "v2"), Arrays.asList(1, 0));

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
	public void requestVolumeNamesFromShouldChooseLeastFilledInGivenSet() throws Exception {
		VolumeChooser vc = vc(20, Arrays.asList("v1", "v2", "v3"), Arrays.asList(10, 5, 7));

		String[] v1v3 = new String[]{"v1", "v3"};

		// 10 5 7
		assertEquals("v3", vc.requestVolumeNameFrom(v1v3));
		// 10 5 8
		assertEquals("v2", vc.requestVolumeName());
		// 10 6 8
	}

	@Test(expected = IllegalStateException.class)
	public void requestVolumeNamesFromWhenVolumeNotKnown() throws Exception {
		VolumeChooser vc = vc(20, Arrays.asList("v1", "v2"), Arrays.asList(3, 4));
		vc.requestVolumeNameFrom(new String[]{"vXXX"});
	}

	@Test
	public void requestVolumeShouldTakeIntoAccountUpdateVolumes() throws Exception {
		VolumeChooser vc = vc(20, Arrays.asList("v1", "v2", "v3"), Arrays.asList(10, 5, 7));

		// 10 5 7
		assertEquals("v2", vc.requestVolumeName());
		// 10 6 7
		assertEquals("v2", vc.requestVolumeName());
		// 10 7 7

		Map<String, Integer> resetV1 = new HashMap<String, Integer>();
		resetV1.put("v1", 0);
		vc.updateVolumes(resetV1);

		// 0 7 7
		assertEquals("v1", vc.requestVolumeName());
		// 1 7 7
	}

	@Test
	public void releaseVolumeShouldActuallyMakePlace() throws Exception {
		VolumeChooser vc = vc(20, Arrays.asList("v1", "v2"), Arrays.asList(1, 3));
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
		VolumeChooser vc = vc(20, Arrays.asList("v1", "v2"), Arrays.asList(1, 3));
		vc.releaseVolume("v1234");
		assertEquals("v1", vc.requestVolumeName());
	}

	@Test
	public void whenFullShouldThrowException() throws Exception {
		VolumeChooser vc = vc(10, Arrays.asList("v1"), Arrays.asList(0));
		// 0
		for (int i = 0; i < 10; i++) {
			assertEquals("v1", vc.requestVolumeName());
		}
		// 10
		try {
			vc.requestVolumeName();
			fail("should have raised an exception");
		} catch (IllegalStateException iaeIgnore) {
		}
	}

	@Test
	public void showPercentage() throws Exception {
		VolumeChooser vc = vc(10, Arrays.asList("v1", "v2", "v3"), Arrays.asList(50, 70, 65));
		vc.maxLUN = 100;		
		assertEquals((double)(50+70+65)/(3*100.0), vc.percentageConsumed(), 1e-6);		
	}
	
	private Map<String, Integer> volumes(List<String> names, List<Integer> values) {
		HashMap<String, Integer> result = new HashMap<String, Integer>();
		int i = 0;
		for (String name : names) {
			result.put(name, values.get(i++));
		}
		return result;
	}

	private VolumeChooser vc(int threshold, List<String> names, List<Integer> values) {
		VolumeChooser vc = VolumeChooser.getInstance();
		vc.maxLUN = threshold;
		vc.updateVolumes(volumes(names, values));
		return vc;
	}

}
