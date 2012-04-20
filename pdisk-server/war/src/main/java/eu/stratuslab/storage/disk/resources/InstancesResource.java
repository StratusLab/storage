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

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.List;
import java.util.Map;

import org.restlet.representation.Representation;
import org.restlet.resource.Get;

import eu.stratuslab.storage.persistence.Instance;
import eu.stratuslab.storage.persistence.InstanceView;

public class InstancesResource extends DiskBaseResource {

	@Get("html")
	public Representation getAsHtml() {

		Map<String, Object> info = listInstances();

		return createTemplateRepresentation("html/instances.ftl", info, TEXT_HTML);
	}

	@Get("json")
	public Representation getAsJson() {

		Map<String, Object> info = listInstances();

		return createTemplateRepresentation("json/instances.ftl", info,
				APPLICATION_JSON);

	}

	private Map<String, Object> listInstances() {
		Map<String, Object> info = createInfoStructure("Instances list");

		addCreateFormDefaults(info);

		String username = getUsername(getRequest());
		List<InstanceView> instances;
		if(isSuperUser(username)){
			instances = Instance.listAll();
		} else {
			instances = Instance.listAllByUser(username);			
		}
		info.put("instances", instances);

		return info;
	}

}
