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

import static org.restlet.data.MediaType.TEXT_HTML;
import static org.restlet.data.MediaType.TEXT_PLAIN;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.zookeeper.KeeperException;
import org.restlet.Request;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class DiskResource extends BaseResource {

	@Get("txt")
	public Representation toText() {
		Representation tpl = templateRepresentation("/text/disk.ftl");

		Map<String, Object> infoTree = new HashMap<String, Object>();
		infoTree.put("properties", loadProperties());

		return new TemplateRepresentation(tpl, infoTree, TEXT_PLAIN);
	}

	@Get("html")
	public Representation toHtml() {
		Representation tpl = templateRepresentation("/html/disk.ftl");

		Map<String, Object> infoTree = new HashMap<String, Object>();
		infoTree.put("properties", loadProperties());

		return new TemplateRepresentation(tpl, infoTree, TEXT_HTML);
	}

	@Delete
	public void removeDisk() {
		String uuid = getDiskId();
		File diskLocation = new File(PersistentDiskApplication.DISK_STORE, uuid);
		File contentsFile = new File(diskLocation, "contents");

		deleteRecursiveZkDiskProperties(getZkDiskPath());

		if (!contentsFile.delete()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot delete " + contentsFile);
		}

		if (!diskLocation.delete()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot delete " + diskLocation);
		}

		// Sleep for a couple of seconds to see if this resolves an issue with
		// the deleted file still being visible.
		try {
			Thread.sleep(2000);
		} catch (InterruptedException consumed) {
		}

		// FIXME: This should probably be done earlier.
		try {
			DiskUtils.restartServer();
		} catch (IOException e) {
			LOGGER.severe("error restarting server: " + e.getMessage());
		}

		// TODO: Redirect user to main page
	}

	private String getZkDiskPath() {
		return buildZkDiskPath(getDiskId());
	}

	private Properties loadProperties() {
		Properties properties;
		String zkPropertiesPath = getZkDiskPath();

		if (!zkPathExists(zkPropertiesPath)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"uuid does not exists");
		}

		try {
			properties = loadZkProperties(zkPropertiesPath);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"unable to retrieve properties");
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"unable to retrieve properties");
		}

		return properties;
	}

	private String getDiskId() {

		Request request = getRequest();

		Map<String, Object> attributes = request.getAttributes();

		return attributes.get("uuid").toString();
	}
}