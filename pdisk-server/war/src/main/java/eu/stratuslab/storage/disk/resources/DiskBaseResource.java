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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.CompressedDiskRemoval;
import eu.stratuslab.storage.disk.utils.MiscUtils;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.Disk.DiskType;

public class DiskBaseResource extends BaseResource {

	private static final String SIZE_KEY = "size";
	private static final String VISIBILITY_KEY = "visibility";
	private static final String TAG_KEY = "tag";
	private static final String OWNER_KEY = "owner";
	private static final String QUARANTINE_START_DATE_KEY = "quarantine";
	private static final String IDENTIFIER_KEY = "identifier";
	private static final String DISK_TYPE_KEY = "type";

	protected Disk getDisk(Form form) {

		return processWebForm(form);
	}

	protected Disk processWebForm(Form form) {
		Disk disk = initializeDisk();
		return processWebForm(disk, form);
	}

	protected Disk processWebForm(Disk disk, Form form) {

		String sizeInForm = form.getFirstValue(SIZE_KEY);
		if (sizeInForm != null) {

			long size;
			try {
				size = Long.parseLong(sizeInForm);
			} catch (NumberFormatException e) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
						"Error parsing size: " + e.getMessage());
			}

			disk.setSize(size);
		}

		String visibilityInForm = form.getFirstValue(VISIBILITY_KEY);
		if (visibilityInForm != null) {
			DiskVisibility visibility;
			try {
				visibility = DiskVisibility.valueOf(visibilityInForm
						.toUpperCase());
			} catch (IllegalArgumentException ex) {
				throw (new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
						"Invalid value for form element visibility: "
								+ ex.getMessage()));
			}

			disk.setVisibility(visibility);
		}

		String tagInForm = form.getFirstValue(TAG_KEY);
		if (tagInForm != null) {
			disk.setTag(tagInForm);
		}

		String ownerInForm = form.getFirstValue(OWNER_KEY);
		if (ownerInForm != null) {
			disk.setOwner(ownerInForm);
		}

		String quarantineStartDate = form.getFirstValue(QUARANTINE_START_DATE_KEY);
		if (quarantineStartDate != null) {
			disk.setQuarantine(quarantineStartDate);
		}

		String identifier = form.getFirstValue(IDENTIFIER_KEY);
		if (identifier != null) {
			disk.setIdentifier(identifier);
		}

		String type = form.getFirstValue(DISK_TYPE_KEY);
		if (type != null) {
			disk.setType(DiskType.valueOf(type));
		}

		return disk;
	}

	protected Disk initializeDisk() {
		Disk disk = new Disk();
		disk.setOwner(getUsername(getRequest()));
		return disk;
	}

	protected void validateDisk(Disk disk) {
		List<String> errors = new LinkedList<String>();

		if (Disk.identifierExists(disk.getIdentifier(), disk.getOwner())) {
			errors.add("image already registered");
		}

		long gigabytes = disk.getSize();

		if (gigabytes < ServiceConfiguration.DISK_SIZE_MIN
				|| gigabytes > ServiceConfiguration.DISK_SIZE_MAX) {
			errors.add("Size must be an integer between "
					+ ServiceConfiguration.DISK_SIZE_MIN + " and "
					+ ServiceConfiguration.DISK_SIZE_MAX);
		}

		if (errors.size() > 0) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					MiscUtils.join(errors, "\", \""));
		}
	}

	protected Disk updateDisk(Disk disk) {
		disk.store();
		return disk;
	}

	protected int incrementUserCount(String uuid) {
		return Disk.load(uuid).incrementUserCount();
	}

	protected Disk loadExistingDisk() {
		Disk disk = Disk.load(getDiskId());
		if (disk == null) {
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "disk ("
					+ getDiskId() + ") does not exist");
		}
		return disk;
	}

	protected String getDiskId() {
		Map<String, Object> attributes = getRequest().getAttributes();

		return attributes.get("uuid").toString();
	}

	protected void cleanCache(String uuid) {
		CompressedDiskRemoval deleteDisk = new CompressedDiskRemoval(uuid);

		getLogger().info("DiskResource toZip: " + uuid);

		Disk disk = loadExistingDisk();
		checkViewRightsOrError(disk);

		deleteDisk.run();
	}

	private void checkViewRightsOrError(Disk disk) {
		if (!hasSufficientRightsToView(disk)) {
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "disk ("
					+ disk.getUuid() + ") does not exist");
		}
	}

	protected void addCreateFormDefaults(Map<String, Object> info) {
		Map<String, Object> defaults = new HashMap<String, Object>();
		defaults.put(Disk.DISK_SIZE_KEY, 1);
		defaults.put(Disk.DISK_VISIBILITY_KEY,
				DiskVisibility.PRIVATE.toString());
	
		info.put("values", defaults);
		
		List<String> visibilities = new LinkedList<String>();
		for (DiskVisibility visibility : DiskVisibility.values()) {
			visibilities.add(visibility.toString());
		}
	
		info.put("visibilities", visibilities);
		
		List<String> types = new LinkedList<String>();
		for (DiskType type : DiskType.values()) {
			types.add(type.toString());
		}
	
		info.put("types", types);
	}

}
