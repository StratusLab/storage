package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.List;

public class VolumeChooserTestHelper {

	public synchronized static VolumeChooser vc(List<String> names, List<Integer> values) {
		VolumeChooser vc = VolumeChooser.getInstance();
		vc.volumes = new HashMap<String, Integer>();
		
		for (int i = 0; i < names.size(); i++) {
			vc.updateVolume(names.get(i), values.get(i));			
		}		
		return vc;
	}
}
