/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.resources;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class DisksResource extends BaseResource {

	@Get
	public Representation getDisksList() {
		Map<String, Object> infos = createInfoStructure("Disks list");
		infos.putAll(listDisks());

		return directTemplateRepresentation("disks.ftl", infos);
	}

	private Map<String, Object> listDisks() {
		Map<String, Object> info = new HashMap<String, Object>();
		List<Properties> diskInfoList = new LinkedList<Properties>();
		List<String> disks = zk.getDisks();

		info.put("disks", diskInfoList);

		for (String uuid : disks) {
			Properties properties = zk.getDiskProperties(getDiskZkPath(uuid));

			// List only disk of the user
			if (hasSufficientRightsToView(properties)) {
				diskInfoList.add(properties);
			}
		}

		return info;
	}
}
