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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;

public class DiskResource extends BaseResource {

	private final static String RESPONSE_TRUE = "1";
	private final static String RESPONSE_FALSE = "0";
	
	@Get
	public Representation getDiskProperties() {
		if (hasQueryString("json")) {
			return getJsonDiskProperties();
		}

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

	public Representation getJsonDiskProperties() {
		Map<String, Object> info = new HashMap<String, Object>();
		info.put("properties", loadProperties());

		return createTemplateRepresentation("json/disk.ftl", info, TEXT_PLAIN);
	}

	private Properties loadProperties() {
		String propertiesPath = getDiskZkPath();

		return zk.getDiskProperties(propertiesPath);
	}
	
	@Post
	public Representation manageDiskUser(Representation entity) {
		PersistentDiskApplication.checkEntity(entity);
		PersistentDiskApplication.checkMediaType(entity.getMediaType());
		
		Form form = new Form(entity);

		for (String query : form.getNames()) {
			
			if (query.equalsIgnoreCase("available")) {
				return diskAvailable();
			} else if (query.equalsIgnoreCase("attach") ) {
				return attachDisk();
			} else if (query.equalsIgnoreCase("detach")) {
				return detachDisk();
			}
		}
		
		return new StringRepresentation(RESPONSE_FALSE);
	}
	
	private Boolean isDiskAvailable() {
		return zk.isDiskAvailable(getDiskZkPath());
	}
	
	private Representation diskAvailable() {
		if (isDiskAvailable()) {
			return new StringRepresentation(RESPONSE_TRUE);
		} else {
			return new StringRepresentation(RESPONSE_FALSE);
		}
	}
	
	private Representation attachDisk() {
		if (isDiskAvailable()) {
			zk.addDiskUser(getDiskZkPath());
			return new StringRepresentation(RESPONSE_TRUE);
		}

		return new StringRepresentation(RESPONSE_FALSE);
	}
	
	private Representation detachDisk() {
		if (zk.getDiskUsers(getDiskZkPath()) > 0) {
			zk.removeDiskUser(getDiskZkPath());
			return new StringRepresentation(RESPONSE_TRUE);
		}

		return new StringRepresentation(RESPONSE_FALSE);
	}
	
	@Delete
	public Representation deleteDisk() {
		Representation rep = null;
		String uuid = getDiskId();
		Properties diskProperties = loadProperties();

		if (!hasSuficientRightsToDelete(diskProperties)) {
			// TODO: Use queue messaging system here
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Not enought rights to delete disk");
		}

		zk.deleteDiskProperties(getDiskZkPath());
		DiskUtils.updateISCSIConfiguration();
		removeDisk(uuid);

		if (hasQueryString("json")) {
			setStatus(Status.SUCCESS_OK);
			rep = new StringRepresentation(uuid, TEXT_PLAIN);
		} else {
			// TODO: Use queue messaging system here
			redirectSeeOther(getBaseUrl() + "/disks/?deleted");
		}
		
		return rep;
	}

	private static void removeDisk(String uuid) {
		if (PersistentDiskApplication.DISK_TYPE == PersistentDiskApplication.DiskType.FILE) {
			removeFileDisk(uuid);
		} else {
			removeLVMDisk(uuid);
		}
	}

	private static void removeFileDisk(String uuid) {
		File diskFile = new File(PersistentDiskApplication.FILE_DISK_LOCATION,
				uuid);

		if (diskFile.delete()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"An error occcured while removing disk content " + uuid);
		}
	}

	private static void removeLVMDisk(String uuid) {
		String volumePath = PersistentDiskApplication.LVM_GROUPE_PATH + "/"
				+ uuid;
		ProcessBuilder pb = new ProcessBuilder(
				PersistentDiskApplication.LVREMOVE_CMD, "-f", volumePath);

		ProcessUtils.execute("removeLVMDisk", pb,
			"It's possible that the disk " + uuid + " is still logged on a node.");
	}
	
	private String getDiskZkPath() {
		return getDiskZkPath(getDiskId());
	}
	
	private String getDiskId() {
		Map<String, Object> attributes = getRequest().getAttributes();

		return attributes.get("uuid").toString();
	}

}
