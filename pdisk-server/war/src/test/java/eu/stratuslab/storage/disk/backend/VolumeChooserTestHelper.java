package eu.stratuslab.storage.disk.backend;

import java.util.List;

public class VolumeChooserTestHelper {

	public static VolumeChooser vc(List<String> names, List<Integer> values) {
		VolumeChooser vc = VolumeChooser.getInstance();
				
		for (int i = 0; i < names.size(); i++) {
			vc.updateVolume(names.get(i), values.get(i));			
		}		
		return vc;
	}
}
