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

import static org.restlet.data.MediaType.APPLICATION_WWW_FORM;
import static org.restlet.data.MediaType.TEXT_HTML;
import static org.restlet.data.MediaType.TEXT_PLAIN;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.zookeeper.KeeperException;
import org.restlet.Request;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class DisksResource extends BaseResource {

	@Post
	public Representation createDisk(Representation entity) {

		if (entity == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"post with null entity");
		}

		MediaType mediaType = entity.getMediaType();

		Properties diskProperties = null;
		if (APPLICATION_WWW_FORM.equals(mediaType, true)) {
			diskProperties = processWebForm();
		} else {
			throw new ResourceException(
					Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,
					mediaType.getName());
		}

		validateDiskProperties(diskProperties);

		initializeDisk(diskProperties);

		try {
			DiskUtils.restartServer();
		} catch (IOException e) {
			// Log this.
			LOGGER.severe("error restarting server: " + e.getMessage());
		}

		String uuid = diskProperties.getProperty(UUID_KEY);

		setStatus(Status.SUCCESS_CREATED);
		Representation rep = new StringRepresentation("disk created: " + uuid,
				TEXT_PLAIN);

		String diskRelativeUrl = "/disks/" + uuid;
		rep.setLocationRef(getRequest().getResourceRef().getIdentifier()
				+ diskRelativeUrl);

		return rep;

	}

	@Get("txt")
	public Representation toText() {
		Map<String, Object> links = listDisks();
		return linksToText(links);
	}

	@Get("html")
	public Representation toHtml() {
		Map<String, Object> links = listDisks();
		return linksToHtml(links);
	}

	private Properties processWebForm() {
		Properties properties = initializeProperties();

		Request request = getRequest();
		Representation entity = request.getEntity();
		Form form = new Form(entity);
		for (String name : form.getNames()) {
			String value = form.getFirstValue(name);
			if (value != null) {
				properties.put(name, value);
			}
		}

		return properties;
	}

	private static Properties initializeProperties() {
		Properties properties = new Properties();
		String uuid = UUID.randomUUID().toString();
		properties.put(UUID_KEY, uuid);
		return properties;
	}

	private void validateDiskProperties(Properties diskProperties) {

		if (!diskProperties.containsKey(UUID_KEY)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"missing UUID for disk");
		}

		if (!diskProperties.containsKey("size")) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"size must be specified");
		}

		String size = diskProperties.getProperty("size");

		try {
			int gigabytes = Integer.parseInt(size);
			if (gigabytes <= 0) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
						"size must be a positive integer");
			}
		} catch (NumberFormatException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"size must be a valid long value");
		}

	}

	private void initializeDisk(Properties properties) {

		String uuid = properties.getProperty(UUID_KEY);

		File diskLocation = new File(PersistentDiskApplication.DISK_STORE, uuid);
		File contentsFile = new File(diskLocation, "contents");

		if (diskLocation.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"disk already exists: " + uuid);
		}

		if (!diskLocation.mkdirs()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot write to disk store: " + diskLocation);
		}

		if (!diskLocation.canWrite()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot write to disk store: " + diskLocation);
		}

		storeDiskProperties(properties);
		initializeDiskContents(properties, contentsFile);

	}

	private void storeDiskProperties(Properties properties) {
		String diskRoot = buildZkDiskPath(properties.get(UUID_KEY).toString());
		Enumeration<?> propertiesEnum = properties.propertyNames();

		// Check if the UUID already exists
		if (zkPathExists(diskRoot)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"disk with same uuid already exists");
		}

		// Create disk entry
		createZkNode(diskRoot, properties.get(UUID_KEY).toString());

		while (propertiesEnum.hasMoreElements()) {
			String key = (String) propertiesEnum.nextElement();
			String content = properties.getProperty(key);

			if (key == UUID_KEY)
				continue;

			createZkNode(diskRoot + "/" + key, content);
		}
	}

	private void initializeDiskContents(Properties properties, File location) {

		try {
			if (!location.createNewFile()) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"contents file already exists: " + location);
			}

			int sizeInGB = Integer.parseInt(properties.get("size").toString());
			zeroFile(location, sizeInGB);

		} catch (IOException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot create contents file: " + location);
		}

	}

	private void zeroFile(File file, int sizeInGB) {

		try {

			DiskUtils.zeroFile(file, sizeInGB);

		} catch (IOException e) {

			if (!file.delete()) {
				// TODO: Log this.
				throw new ResourceException(
						Status.SERVER_ERROR_INSUFFICIENT_STORAGE,
						e.getMessage() + "; cannot delete resource");
			}

			throw new ResourceException(
					Status.SERVER_ERROR_INSUFFICIENT_STORAGE, e.getMessage());

		}
	}

	private Representation linksToHtml(Map<String, Object> infoTree) {
		Representation tpl = templateRepresentation("/html/disks.ftl");
		return new TemplateRepresentation(tpl, infoTree, TEXT_HTML);
	}

	private Representation linksToText(Map<String, Object> infoTree) {
		Representation tpl = templateRepresentation("/text/disks.ftl");
		return new TemplateRepresentation(tpl, infoTree, TEXT_PLAIN);
	}

	private Map<String, Object> listDisks() {
		Map<String, Object> info = new HashMap<String, Object>();
		List<Properties> diskInfoList = new LinkedList<Properties>();
		info.put("disks", diskInfoList);

		try {
			List<String> disks = zk.getChildren(
					PersistentDiskApplication.ZK_ROOT_PATH, false);

			for (String uuid : disks) {
				Properties properties = loadZkProperties(buildZkDiskPath(uuid));
				// FIXME: Why do we not use directly uuid instead of link?
				properties.put("link", uuid);
				diskInfoList.add(properties);
			}
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"unable to retrieve disks properties: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"unable to retrieve disks properties: " + e.getMessage());
		}
		
		return info;
	}
}
