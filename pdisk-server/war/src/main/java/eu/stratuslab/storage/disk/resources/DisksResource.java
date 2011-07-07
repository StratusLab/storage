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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;

public class DisksResource extends BaseResource {

	@Get
	public Representation getDisksList() {
		if (hasQueryString("json")) {
			return getJsonDiskList();
		}

		Map<String, Object> infos = createInfoStructure("Disks list");

		infos.putAll(listDisks());

		if (hasQueryString("deleted")) {
			// TODO: Use queue messaging system here
			infos.put("deleted", true);
		}

		return createTemplateRepresentation("html/disks.ftl", infos, TEXT_HTML);
	}

	public Representation getJsonDiskList() {
		Map<String, Object> infos = listDisks();
		return createTemplateRepresentation("json/disks.ftl", infos, TEXT_PLAIN);
	}

	private Map<String, Object> listDisks() {
		Map<String, Object> info = new HashMap<String, Object>();
		List<Properties> diskInfoList = new LinkedList<Properties>();
		List<String> disks = zk.getDisks();

		info.put("disks", diskInfoList);

		for (String uuid : disks) {
			Properties properties = zk.getDiskProperties(getDiskZkPath(uuid));

			// List only disk of the user
			if (hasSuficientRightsToView(properties)) {
				diskInfoList.add(properties);
			}
		}

		return info;
	}

	@Post
	public void createDisk(Representation entity) {
		checkEntity(entity);
		checkMediaType(entity.getMediaType());

		Properties diskProperties = processWebForm();
		String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

		validateDiskProperties(diskProperties);
		initializeDisk(diskProperties);
		DiskUtils.updateISCSIConfiguration();

		if (hasQueryString("json")) {
			setStatus(Status.SUCCESS_CREATED);
		} else {
			// TODO: Use queue messaging system here
			redirectSeeOther(getBaseUrl() + "/disks/" + uuid + "/?created");
		}
	}

	private void checkEntity(Representation entity) {
		if (entity == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"post with null entity");
		}
	}

	private void checkMediaType(MediaType mediaType) {
		if (!APPLICATION_WWW_FORM.equals(mediaType, true)) {
			throw new ResourceException(
					Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,
					mediaType.getName());
		}
	}

	private Properties processWebForm() {
		Properties properties = initializeProperties();
		Representation entity = getRequest().getEntity();
		Form form = new Form(entity);

		for (String name : form.getNames()) {
			String value = form.getFirstValue(name);
			if (value != null) {
				properties.put(name, value);
			}
		}

		return properties;
	}

	private Properties initializeProperties() {
		Properties properties = new Properties();
		properties.put(DiskProperties.UUID_KEY, generateUUID());
		properties.put(DiskProperties.DISK_OWNER_KEY, getUsername());
		properties.put(DiskProperties.DISK_CREATION_DATE_KEY, getDateTime());

		return properties;
	}

	private static String generateUUID() {
		return UUID.randomUUID().toString();
	}

	private void validateDiskProperties(Properties diskProperties) {
		// TODO: Use queue messaging system here
		try {
			diskVisibilityFromString(diskProperties.getProperty("visibility",
					"None"));
		} catch (RuntimeException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Invalid disk visibility");
		}

		// TODO: Use queue messaging system here
		try {
			String size = diskProperties.getProperty("size", "None");
			int gigabytes = Integer.parseInt(size);

			if (gigabytes < PersistentDiskApplication.DISK_SIZE_MIN
					|| gigabytes > PersistentDiskApplication.DISK_SIZE_MAX) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
						"Size must be an integer between "
								+ PersistentDiskApplication.DISK_SIZE_MIN
								+ " and "
								+ PersistentDiskApplication.DISK_SIZE_MAX);
			}
		} catch (NumberFormatException e) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Size must be a valid positive integer.");
		}

	}

	private static void initializeDisk(Properties properties) {
		String uuid = properties.getProperty(DiskProperties.UUID_KEY).toString();
		int size = Integer.parseInt(properties.getProperty("size").toString());

		if (PersistentDiskApplication.DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			initializeFileDisk(uuid, size);
		} else {
			initializeLVMDisk(uuid, size);
		}

		saveDiskProperties(properties);
	}

	private static void initializeFileDisk(String uuid, int size) {
		File diskFile = new File(PersistentDiskApplication.FILE_DISK_LOCATION,
				uuid);

		if (diskFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"A disk with the same name already exists.");
		}

		FileUtils.createZeroFile(diskFile, size);
	}

	private static void initializeLVMDisk(String uuid, int size) {
		File diskFile = new File(PersistentDiskApplication.LVM_GROUPE_PATH,
				uuid);

		if (diskFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"A disk with the same name already exists.");
		}

		String lvmSize = size + "G";
		ProcessBuilder pb = new ProcessBuilder(
				PersistentDiskApplication.LVCREATE_CMD, "-L", lvmSize,
				PersistentDiskApplication.LVM_GROUPE_PATH, "-n", uuid);

		ProcessUtils.execute("createLvmDisk", pb);
	}

	private static void saveDiskProperties(Properties properties) {
		String diskRoot = getDiskZkPath(properties.get(DiskProperties.UUID_KEY).toString());
		Enumeration<?> propertiesEnum = properties.propertyNames();

		zk.createNode(diskRoot, properties.get(DiskProperties.UUID_KEY).toString());

		while (propertiesEnum.hasMoreElements()) {
			String key = (String) propertiesEnum.nextElement();
			String content = properties.getProperty(key);

			if (key == DiskProperties.UUID_KEY)
				continue;

			zk.createNode(diskRoot + "/" + key, content);
		}
	}

}
