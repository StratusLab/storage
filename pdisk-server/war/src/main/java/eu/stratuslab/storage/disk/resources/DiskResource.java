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
import org.restlet.routing.Redirector;

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
		Map<String, Object> infos = createInfoStructure("Disk info");
		infos.put("properties", loadProperties());
		infos.put("url", getRequest().getResourceRef().toString());
			
		String queryString = getRequest().getResourceRef().getQuery();
		if (queryString != null && queryString.equals("created")) {
			infos.put("created", true);
		}
		
		return createTemplateRepresentation("html/disk.ftl", infos, TEXT_HTML);
	}

	@Delete
	public void removeDisk() {
		String uuid = getDiskId();
		File diskLocation = new File(PersistentDiskApplication.DISK_STORE, uuid);

		deleteRecursiveZkDiskProperties(getZkDiskPath());

		if (!deleteDisk(uuid)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"cannot delete disk content: " + uuid);
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

		redirectSeeOther("/disks/?deleted");
	}

	private static Boolean deleteDisk(String uuid) {
		if (PersistentDiskApplication.DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			File diskContents = new File(PersistentDiskApplication.DISK_STORE,
					uuid + "/contents");

			return diskContents.delete();
		} else {
			try {
				return deleteLVMDisk(uuid);
			} catch (IOException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"an error occured while removing volume " + uuid);
			}
		}
	}

	private static Boolean deleteLVMDisk(String uuid) throws IOException {
		File lvremoveBin = new File(PersistentDiskApplication.LVREMOVE_DIR,
				"lvremove");
		String volumePath = PersistentDiskApplication.LVM_GROUPE_PATH + "/"
				+ uuid;

		if (lvremoveBin.canExecute()) {
			ProcessBuilder pb = new ProcessBuilder(
					lvremoveBin.getAbsolutePath(), "-f", volumePath);
			Process process = pb.start();

			boolean blocked = true;
			while (blocked) {
				try {
					process.waitFor();
					blocked = false;
				} catch (InterruptedException consumed) {
					// Just continue with the loop.
				}
			}
			int rc = process.exitValue();

			if (rc != 0) {
				LOGGER.severe("lvcreate command failled: " + rc);
				return false;
			} else {
				return true;
			}
		} else {
			LOGGER.severe("cannot execute lvcreate command");
			return false;
		}
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
