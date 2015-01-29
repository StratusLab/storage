package eu.stratuslab.storage.backend;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.Test;

import eu.stratuslab.storage.disk.backend.BackendChecker;
import eu.stratuslab.storage.disk.backend.VolumeChooser;

public class BackendCheckerTest {

	@Test
	public void testBackendChecker() {
		new BackendChecker();
		VolumeChooser vc = VolumeChooser.getInstance();
		//assertEquals(new HashMap<String, Integer>(), vc.getVolumes());
	}

//	@Test
//	public void testRun() {
//		new BackendChecker().run();
//	}

}
