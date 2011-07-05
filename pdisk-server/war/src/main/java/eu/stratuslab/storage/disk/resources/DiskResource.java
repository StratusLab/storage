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
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.zookeeper.KeeperException;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class DiskResource extends BaseResource {

	@Get
	public Representation getDiskProperties() {
		Map<String, Object> infos = createInfoStructure("Disk info");
		Properties diskProperties = loadProperties();
		
		
		if (!hasSuficientRightsToView(diskProperties)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Not enought rights to display disk properties");
		}
		
		infos.put("properties", diskProperties);
		infos.put("url", getCurrentUrl());

		if (hasQueryString("created")) {
			infos.put("created", true);
		}
		
		if (hasSuficientRightsToDelete(diskProperties)) {
			infos.put("can_delete", true);
		}

		return createTemplateRepresentation("html/disk.ftl", infos, TEXT_HTML);
	}

	private Properties loadProperties() {
		Properties properties;
		String zkPropertiesPath = getZkDiskPath();

		if (!zkPathExists(zkPropertiesPath)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"UUID does not exists");
		}

		try {
			properties = loadZkProperties(zkPropertiesPath);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to retrieve properties");
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to retrieve properties");
		}

		return properties;
	}

	private String getZkDiskPath() {
		return buildZkDiskPath(getDiskId());
	}

	private String getDiskId() {
		Map<String, Object> attributes = getRequest().getAttributes();

		return attributes.get("uuid").toString();
	}

	@Delete
	public void deleteDisk() {
		String uuid = getDiskId();
		Properties diskProperties = loadProperties();
		
		if (!hasSuficientRightsToDelete(diskProperties)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Not enought rights to delete disk");
		}

		deleteRecursiveZkDiskProperties(getZkDiskPath());
		removeDisk(uuid);
		restartServer();

		redirectSeeOther(getApplicationBaseUrl() + "/disks/?deleted");
	}

	private static void removeDisk(String uuid) {
		if (PersistentDiskApplication.DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			removeFileDisk(uuid);
		} else {
			removeLVMDisk(uuid);
		}
	}

	private static void removeFileDisk(String uuid) {
		File diskFile = new File(PersistentDiskApplication.DISK_STORE, uuid);

		if (diskFile.delete()) {
			LOGGER.severe("An error occcured while removing disk content "
					+ uuid);
		}
	}

	private static void removeLVMDisk(String uuid) {
		File lvremoveBin = new File(PersistentDiskApplication.LVREMOVE_DIR,
				"lvremove");
		String volumePath = PersistentDiskApplication.LVM_GROUPE_PATH + "/"
				+ uuid;

		if (!lvremoveBin.canExecute()) {
			LOGGER.severe("Cannot execute lvcreate command");
			return;
		}

		int returnCode = 1;
		Process process;
		ProcessBuilder pb = new ProcessBuilder(lvremoveBin.getAbsolutePath(),
				"-f", volumePath);

		try {
			process = pb.start();

			boolean blocked = true;
			while (blocked) {
				process.waitFor();
				blocked = false;
			}

			returnCode = process.exitValue();
		} catch (IOException e) {
			LOGGER.severe("An error occured while removing LVM volume " + uuid);
		} catch (InterruptedException consumed) {
			// Just continue with the loop.
		}

		if (returnCode != 0) {
			LOGGER.severe("lvcreate command failled for disk " + uuid + ": "
					+ returnCode);
		}
	}

}
