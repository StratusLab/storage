package eu.stratuslab.storage.disk.backend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VolumeChooserTestHelper {

	public static VolumeChooser vc(List<String> names, List<Integer> values) {
		VolumeChooser vc = VolumeChooser.getInstance();		
		vc.updateVolumes(volumes(names, values));
		return vc;
	}

	private static Map<String, Integer> volumes(List<String> names, List<Integer> values) {
		HashMap<String, Integer> result = new HashMap<String, Integer>();
		int i = 0;
		for (String name : names) {
			result.put(name, values.get(i++));
		}
		return result;
	}
}
