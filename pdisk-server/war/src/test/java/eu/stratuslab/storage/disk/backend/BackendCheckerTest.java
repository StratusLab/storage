package eu.stratuslab.storage.disk.backend;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BackendCheckerTest {

	@Before
	public void resetVolumeChooser() {
		VolumeChooser.getInstance().volumes = null;
	}

	@Test(expected = IllegalStateException.class)
	public void testBackendChecker() {

		BackendChecker bc = new BackendChecker() {
			protected String[] getBackendProxiesFromConfig() {
				return new String[] {};
			}
		};
		bc.update();

		VolumeChooser.getInstance().requestVolumeName();
	}

	@Test
	public void testBackendCheckerWithOneVolume() {

		BackendChecker bc = new BackendChecker() {
			protected Integer getNumberOfMappedLuns(String volume) {
				return 10;
			}

			protected String[] getBackendProxiesFromConfig() {
				return new String[] { "v1" };
			}
		};
		bc.update();

		Assert.assertEquals("v1", VolumeChooser.getInstance().requestVolumeName());
	}

	@Test
    public void leastFilledVolumeIsReturned() throws Exception {
		BackendChecker bc = new BackendChecker() {
			protected Integer getNumberOfMappedLuns(String volume) {
				if ("v1".equals(volume)) {
	                return 10;
                } else if ("v2".equals(volume)){
                	return 5;
                } else {
                	throw new IllegalArgumentException();
                }
			}
			protected String[] getBackendProxiesFromConfig() {
				return new String[] { "v1", "v2" };
			}
		};
		bc.update();

		Assert.assertEquals("v2", VolumeChooser.getInstance().requestVolumeName());

    }

}
