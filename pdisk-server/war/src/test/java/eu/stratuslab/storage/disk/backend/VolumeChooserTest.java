package eu.stratuslab.storage.disk.backend;

import static eu.stratuslab.storage.disk.backend.VolumeChooserTestHelper.vc;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class VolumeChooserTest {
	
	@Before
	public void resetVolumes() {
		URL configFile = this.getClass().getResource("/pdisk.test.cfg");
		System.setProperty("pdisk.config.filename", configFile.getFile());
		
		VolumeChooser.logger.setLevel(Level.SEVERE);		
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

	@Test(expected = ResourceException.class)
	public void requestVolumeNameShouldForbidEmptyList() throws Exception {
		vc(Arrays.asList("v1", "v2"), Arrays.asList(1, 0));
		VolumeChooser.getInstance().requestVolumeNameFrom(new String[0]);
	}
	
	@Test(expected = ResourceException.class)
	public void requestVolumeNamesFromWhenVolumeNotKnown() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2"), Arrays.asList(3, 4));
		vc.requestVolumeNameFrom(new String[]{"vXXX"});
	}

	@Test
	public void partialUpdateVolumes() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1"), Arrays.asList(0));		
		assertEquals("v1", vc.requestVolumeName());				
		
		vc.updateVolume("v1", vc.maxLUN);
		vc.updateVolume("v2", vc.maxLUN/2);		
		assertEquals("v2", vc.requestVolumeName());
	}
	
	@Test
	public void requestVolumeShouldTakeIntoAccountUpdateVolumes() throws Exception {
		int maxLUN = VolumeChooser.getInstance().maxLUN;
		VolumeChooser vc = vc(Arrays.asList("v1", "v2", "v3"), Arrays.asList(maxLUN, maxLUN, maxLUN));

		try {
			vc.requestVolumeName();
			fail("Exception expected");
		} catch (Exception ignore) {
		}
		
		vc.updateVolume("v1", 0);
		Assert.assertEquals("v1", vc.requestVolumeName());
	}
	
	@Test
	public void unknownReleaseVolumeShouldBeIgnored() throws Exception {		
		VolumeChooser vc = vc(Arrays.asList("v1", "v2"), Arrays.asList(1, 3));
		vc.releaseVolume("v1234");
		assertNotNull(vc.requestVolumeName());
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
	public void volumesShouldBeServedUniformly() throws Exception {
		int nbVolumes = 20;
		List<String> volumeNames = new ArrayList<String>();
		List<Integer> values = new ArrayList<Integer>();		
		for (int i = 0; i < nbVolumes; i++) {
			volumeNames.add("v"+i);
			values.add(0);
		}
		
		VolumeChooser vc = vc(volumeNames, values);		
		Map<String, Integer> scorePerVolume = new HashMap<String, Integer>();
		
		int idealDistribution = 4;
		int nbRequests = nbVolumes * idealDistribution;	
				
		for (int i = 0; i < nbRequests; i++) {					
			String volumeWinner = vc.requestVolumeNameWithRetryFrom(volumeNames.toArray(new String[0]));					
			Integer score = scorePerVolume.get(volumeWinner);
			if (score==null) {
				score = 0;
			}
			scorePerVolume.put(volumeWinner, score+1);
		}		
						
		for (Integer value : scorePerVolume.values()) {
			Assert.assertEquals(idealDistribution, (int)value);
		}		 
	}
	
	@Test
	public void showPercentage() throws Exception {
		VolumeChooser vc = vc(Arrays.asList("v1", "v2", "v3"), Arrays.asList(50, 70, 65));		
		assertEquals((double)(50+70+65)/(3*vc.maxLUN), vc.percentageConsumed(), 1e-6);		
	}

}
