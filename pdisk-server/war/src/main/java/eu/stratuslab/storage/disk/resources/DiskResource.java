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

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;

public class DiskResource extends DiskBaseResource {

	private static final String UUID_KEY_NAME = DiskProperties.UUID_KEY;
	private Properties diskProperties = null;

	@Override
	protected void doInit() throws ResourceException {

		diskProperties = zk.getDiskProperties(getDiskId());

		if (!hasSufficientRightsToView(diskProperties)) {
			throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
					"insuffient access rights to view disk (" + getDiskId()
							+ ")");
		}

	}

	@Put
	public void update(Representation entity) {

		checkExistance();

		MiscUtils.checkForNullEntity(entity);

		Properties properties = processWebForm(new Form(entity));
		properties.put(UUID_KEY_NAME, getDiskId());

		updateDisk(properties);

	}

	@Post
	public void createCopyOnWriteOrRebase(Representation entity) {
		boolean isCoW = new DiskProperties().isCoW(getDiskId());

		String newUuid = null;
		if (isCoW) {
			newUuid = rebase();
		} else {
			newUuid = createCoW();
		}

		redirectSeeOther(getBaseUrl() + "/disks/" + newUuid + "/");

	}

	private String createCoW() {

		Properties properties = initializeProperties();

		properties.put(DiskProperties.DISK_SIZE_KEY,
				diskProperties.getProperty(DiskProperties.DISK_SIZE_KEY));
		properties.put(DiskProperties.UUID_KEY, getDiskId());

		String cowUuid = DiskUtils.createCoWDisk(properties);

		properties.put(DiskProperties.UUID_KEY, cowUuid);

		incrementOriginDiskUserCount();

		return cowUuid;
	}

	private void incrementOriginDiskUserCount() {
		incrementUserCount(getDiskId());
	}

	private String rebase() {

		Properties properties = getExistingProperties();

		String cowUuid = getDiskId();
		String newUuid = DiskUtils.rebaseDisk(properties);

		Properties newProperties = initializeProperties();
		newProperties.put(DiskProperties.UUID_KEY, newUuid);
		newProperties.put(DiskProperties.DISK_READ_ONLY_KEY, true);
		newProperties.put(DiskProperties.DISK_SIZE_KEY,
				properties.getProperty(DiskProperties.DISK_SIZE_KEY));

		registerDisk(newProperties);

		deleteDisk(cowUuid);

		properties = calculateHashes(properties);
		
	    registerDisk(properties);
		
		return newUuid;
	}

	protected Properties calculateHashes(Properties properties) {
		String identifier;
		try {
			identifier = DiskUtils.calculateHash(properties.getProperty(DiskProperties.UUID_KEY));
		} catch (FileNotFoundException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e.getMessage());
		}
		properties.put(DiskProperties.DISK_IDENTIFER_KEY, identifier);
		
		return properties;
	}

	@Get("html")
	public Representation getAsHtml() {

		getLogger().info("DiskResource getAsHtml: " + getDiskId());

		Map<String, Object> info = listDiskProperties();

		addDiskUserHeader();

		return createTemplateRepresentation("html/disk.ftl", info, TEXT_HTML);
	}

	@Get("json")
	public Representation getAsJson() {

		getLogger().info("DiskResource getAsJson: " + getDiskId());

		Map<String, Object> info = listDiskProperties();

		addDiskUserHeader();

		return createTemplateRepresentation("json/disk.ftl", info,
				APPLICATION_JSON);
	}

	@Delete("html")
	public Representation deleteDiskAsHtml() {

		getLogger().info("DiskResource deleteDiskAsHtml: " + getDiskId());

		processDeleteDiskRequest();

		MESSAGES.push("Your disk have been deleted successfully");
		redirectSeeOther(getBaseUrl() + "/disks/");

		Map<String, Object> info = createInfoStructure("redirect");
		return createTemplateRepresentation("html/redirect.ftl", info,
				TEXT_HTML);
	}

	@Delete("json")
	public Representation deleteDiskAsJson() {

		getLogger().info("DiskResource deleteDiskAsJson: " + getDiskId());

		processDeleteDiskRequest();

		Map<String, Object> info = new HashMap<String, Object>();
		info.put("key", UUID_KEY_NAME);
		info.put("value", getDiskId());

		return createTemplateRepresentation("json/keyvalue.ftl", info,
				APPLICATION_JSON);
	}

	private Map<String, Object> listDiskProperties() {
		Map<String, Object> infos = createInfoStructure("Disk Information");

		checkExistance();

		Properties diskProperties = zk.getDiskProperties(getDiskId());

		infos.put("properties", diskProperties);
		infos.put("url", getCurrentUrl());
		infos.put("can_delete", hasSufficientRightsToDelete(diskProperties));

		return infos;

	}

	private void addDiskUserHeader() {
		Form diskUserHeaders = (Form) getResponse().getAttributes().get(
				"org.restlet.http.headers");

		if (diskUserHeaders == null) {
			diskUserHeaders = new Form();
			getResponse().getAttributes().put("org.restlet.http.headers",
					diskUserHeaders);
		}

		diskUserHeaders.add("X-DiskUser-Limit",
				String.valueOf(RootApplication.CONFIGURATION.USERS_PER_DISK));
		diskUserHeaders.add("X-DiskUser-Remaining",
				String.valueOf(zk.remainingFreeUser(getDiskId())));
	}

	private void processDeleteDiskRequest() {

		String diskId = getDiskId();

		if (!zk.diskExists(diskId)) {
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "disk ("
					+ diskId + ") does not exist");
		}

		Properties diskProperties = zk.getDiskProperties(diskId);

		if (!hasSufficientRightsToDelete(diskProperties)) {
			throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
					"insufficient rights to delete disk (" + diskId + ")");
		}

		if (zk.getNumberOfMounts(diskId) > 0) {
			throw new ResourceException(Status.CLIENT_ERROR_CONFLICT, "disk ("
					+ diskId + ") is in use and can't be deleted");
		}

		deleteDisk();
	}

	private void deleteDisk() {
		deleteDisk(getDiskId());
	}

	private void deleteDisk(String uuid) {
		Properties propreties = zk.getDiskProperties(uuid);		
		zk.deleteRecursively(uuid);
		try{
			DiskUtils.removeDisk(uuid);
		} catch(ResourceException e) {
			registerDisk(propreties);
			throw(e);
		}
		// TODO: decrement user count in parent disk...
	}

}
