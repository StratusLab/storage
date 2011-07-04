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
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class DisksResource extends BaseResource {

	@Get
	public Representation getDisksList() {
		Map<String, Object> infos = createInfoStructure("Disks list");

		infos.putAll(listDisks());

		if (hasQueryString("deleted")) {
			infos.put("deleted", true);
		}

		return createTemplateRepresentation("html/disks.ftl", infos, TEXT_HTML);
	}

	private Map<String, Object> listDisks() {
		Map<String, Object> info = new HashMap<String, Object>();
		List<Properties> diskInfoList = new LinkedList<Properties>();
		info.put("disks", diskInfoList);

		try {
			List<String> disks = getDisks();

			for (String uuid : disks) {
				Properties properties = loadZkProperties(buildZkDiskPath(uuid));

				// List only disk of the user
				if (hasSuficientRights(properties)) {
					diskInfoList.add(properties);
				}
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

	@Post
	public void createDisk(Representation entity) {
		checkEntity(entity);
		checkMediaType(entity.getMediaType());

		Properties diskProperties = processWebForm();
		String uuid = diskProperties.getProperty(UUID_KEY);

		validateDiskProperties(diskProperties);
		initializeDisk(diskProperties);
		restartServer();

		setStatus(Status.SUCCESS_CREATED);
		redirectSeeOther(getApplicationBaseUrl() + "/disks/" + uuid
				+ "/?created");
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
		properties.put(UUID_KEY, generateUUID());
		properties.put(DISK_OWNER_KEY, getUsername());

		return properties;
	}

	private static String generateUUID() {
		return UUID.randomUUID().toString();
	}

	private static void validateDiskProperties(Properties diskProperties) {
		if (!diskProperties.containsKey(UUID_KEY)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"missing UUID for disk");
		}

		if (!diskProperties.containsKey("size")) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"size must be specified");
		}

		try {
			String size = diskProperties.getProperty("size");
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

	private static void initializeDisk(Properties properties) {
		String uuid = properties.getProperty(UUID_KEY).toString();
		int size = Integer.parseInt(properties.getProperty("size").toString());

		if (PersistentDiskApplication.DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			initializeFileDisk(uuid, size);
		} else {
			initializeLVMDisk(uuid, size);
		}

		saveDiskProperties(properties);
	}

	private static void initializeFileDisk(String uuid, int size) {
		File diskFile = new File(PersistentDiskApplication.DISK_STORE, uuid);

		if (diskFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"disk already exists: " + uuid);
		}

		initializeFileDiskContents(diskFile, size);
	}

	private static void initializeLVMDisk(String uuid, int size) {
		File lvcreateBin = new File(PersistentDiskApplication.LVCREATE_DIR,
				"lvcreate");

		if (!lvcreateBin.canExecute()) {
			LOGGER.severe("cannot execute lvcreate command");
			return;
		}

		int returnCode = 1;
		String lvSize = size + "G";
		Process process;
		ProcessBuilder pb = new ProcessBuilder(lvcreateBin.getAbsolutePath(),
				"-L", lvSize, PersistentDiskApplication.LVM_GROUPE_PATH, "-n",
				uuid);

		try {
			process = pb.start();

			boolean blocked = true;
			while (blocked) {
				process.waitFor();
				blocked = false;
			}

			returnCode = process.exitValue();
		} catch (IOException e) {
			LOGGER.severe("an error occured while creating LVM volume " + uuid);
		} catch (InterruptedException consumed) {
			// Just continue with the loop.
		}

		if (returnCode != 0) {
			LOGGER.severe("lvcreate command failled for disk " + uuid + ": "
					+ returnCode);
		}
	}

	private static void saveDiskProperties(Properties properties) {
		String diskRoot = buildZkDiskPath(properties.get(UUID_KEY).toString());
		Enumeration<?> propertiesEnum = properties.propertyNames();

		// Check if the UUID already exists
		if (zkPathExists(diskRoot)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"disk with same uuid already exists");
		}

		createZkNode(diskRoot, properties.get(UUID_KEY).toString());

		while (propertiesEnum.hasMoreElements()) {
			String key = (String) propertiesEnum.nextElement();
			String content = properties.getProperty(key);

			if (key == UUID_KEY)
				continue;

			createZkNode(diskRoot + "/" + key, content);
		}
	}

	private static void initializeFileDiskContents(File location, int size) {
		try {
			if (!location.createNewFile()) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"contents file already exists: " + location);
			}

			zeroFile(location, size);
		} catch (IOException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot create contents file: " + location);
		}
	}

	private static void zeroFile(File file, int sizeInGB) {
		try {
			DiskUtils.zeroFile(file, sizeInGB);
		} catch (IOException e) {

			if (!file.delete()) {
				throw new ResourceException(
						Status.SERVER_ERROR_INSUFFICIENT_STORAGE,
						e.getMessage() + "; cannot delete resource");
			}
			throw new ResourceException(
					Status.SERVER_ERROR_INSUFFICIENT_STORAGE, e.getMessage());
		}
	}

}
