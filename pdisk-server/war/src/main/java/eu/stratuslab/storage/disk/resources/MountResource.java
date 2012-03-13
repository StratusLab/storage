package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.Map;

import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.Mount;

public class MountResource extends BaseResource {

	private String diskId = null;
	private String mountId = null;
	private String node = null;
	private Mount mount = null;

	@Override
	public void doInit() {

		Map<String, Object> attributes = getRequest().getAttributes();

		Object diskIdValue = attributes.get("uuid");
		if (diskIdValue == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"disk UUID cannot be null");
		}
		diskId = diskIdValue.toString();

		Object mountIdValue = attributes.get("mountid");
		if (mountIdValue == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"mount ID cannot be null");

		}
		mountId = mountIdValue.toString();
		
		mount  = Mount.load(mountId);

		if(mount == null) {
			setExisting(false);
		}

	}

	@Get("html")
	public Representation getAsHtml() {

		Map<String, Object> info = getMountProperties();
		return createTemplateRepresentation("html/mount.ftl", info, TEXT_HTML);
	}

	@Get("json")
	public Representation getAsJson() {

		Map<String, Object> info = getMountProperties();
		return createTemplateRepresentation("json/mount.ftl", info,
				APPLICATION_JSON);
	}

	@Delete("html")
	public Representation detachDiskAsHtml(Representation entity) {

		getLogger().info(
				"DiskResource detachDiskAsHtml: " + diskId + ", " + mountId
						+ ", " + node + ", " + mount.getVmId());

		detachHotPluggedDisk();

		redirectSeeOther(getBaseUrl() + "/disks/" + diskId + "/mounts/");

		Map<String, Object> info = createInfoStructure("redirect");
		return createTemplateRepresentation("html/redirect.ftl", info,
				TEXT_HTML);
	}

	@Delete("json")
	public Representation detachDiskAsJson(Representation entity) {

		getLogger().info(
				"DiskResource detachDiskAsJson: " + diskId + ", " + mountId
						+ ", " + node + ", " + mount.getVmId());

		String diskTarget = detachHotPluggedDisk();

		Map<String, Object> info = getMountProperties();
		info.put("target", diskTarget);
		return createTemplateRepresentation("json/mount.ftl", info,
				APPLICATION_JSON);
	}

	private Map<String, Object> getMountProperties() {

		Map<String, Object> info = this
				.createInfoStructure("Mount Information");
		info.put("diskId", diskId);
		info.put("mountId", mountId);
		info.put("vmId", mount.getVmId());
		info.put("device", mount.getDevice());
		info.put("currenturl", getCurrentUrl());
		return info;
	}

	private String detachHotPluggedDisk() {

		Disk disk = Disk.load(diskId);
		if (disk == null) {
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND,
					"unknown disk: " + diskId);
		}

		if (!hasSufficientRightsToView(disk)) {
			throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
					"insufficient rights to detach disk");
		}

		String diskTarget = disk.diskTarget(mount.getId());

		// Only force the dismount if this was mounted through the
		// storage service.
		if (!diskTarget.equals(Disk.STATIC_DISK_TARGET)) {
			
			try {
				DiskUtils.detachHotplugDisk(serviceName(), servicePort(), node,
						mount.getVmId(), diskId, diskTarget);
				getLogger().info(
						"hotDetach: " + node + ", " + mount.getVmId() + ", " + diskId + ", "
								+ diskTarget);
			} catch(ResourceException e) {
				getLogger().warning(
						"hotDetach failed for: " + node + ", " + mount.getVmId() + ", " + diskId + ", "
								+ diskTarget);
				
			}
		}

		mount.remove();
		
		return diskTarget;

	}

}
