package eu.stratuslab.storage.disk.resources;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class ActionResource extends BaseResource {

	private enum DiskAction {
		ATTACH, DETACH, HOT_ATTACH, HOT_DETACH;
	}

	private enum QueryProcessStatus {
		MISSING, UNKNOW, OK;
	}

	private List<String> diskUuids;
	private String node;
	private String vmId;

	@Post
	public Representation actionDispatcher(Representation entity) {
		DiskAction action = getAction();
		QueryProcessStatus processingResult = processRequest(entity);

		if (processingResult == QueryProcessStatus.MISSING) {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"Missing input");
		} else if (processingResult == QueryProcessStatus.UNKNOW) {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"Unknown input");
		}

		if (action == DiskAction.ATTACH) {
			return attachDisk(DiskProperties.STATIC_DISK_TARGET);
		} else if (action == DiskAction.HOT_ATTACH) {
			return attachDisk(zk.nextHotpluggedDiskTarget(node, vmId));
		} else if (action == DiskAction.DETACH) {
			return detachAllDisks();
		} else if (action == DiskAction.HOT_DETACH) {
			return detachHotpluggedDisk();
		} else {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"Requested action does not exist");
		}

	}

	private Representation attachDisk(String target) {
		String diskUuid = getDiskId();

		if (diskUuid == null) {
			return respondError(Status.CLIENT_ERROR_NOT_FOUND, "Disk UUID not provided");
		} else if (!diskExists(diskUuid)) {
			return respondError(Status.CLIENT_ERROR_NOT_FOUND, "Bad disk UUID");
		}

		zk.addDiskUser(node, vmId, diskUuid, target);
		diskUuids = zk.getAttachedDisk(node, vmId);

		if (!target.equals(DiskProperties.STATIC_DISK_TARGET)) {
			DiskUtils.attachHotplugDisk(serviceName(), servicePort(), node,
					vmId, diskUuid, target);
		}

		return actionResponse();
	}

	private Representation detachAllDisks() {
		diskUuids = zk.getAttachedDisk(node, vmId);

		if (zk.removeDiskUser(node, vmId)) {
			return actionResponse();
		} else {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"VM don't have disk attached");
		}
	}

	private Representation detachHotpluggedDisk() {
		String diskUuid = getDiskId();
		List<String> disks = new LinkedList<String>();

		disks.add(diskUuid);
		diskUuids = disks;

		if (diskUuid == null || !diskExists(diskUuid)) {
			return respondError(Status.CLIENT_ERROR_NOT_FOUND, "Bad disk UUID");
		}

		String diskTarget = zk.diskTarget(node, vmId, diskUuid);
		if (diskTarget.equals(DiskProperties.STATIC_DISK_TARGET)) {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"Disk have not been hot-plugged");
		}

		zk.removeDiskUser(node, vmId, diskUuid);
		DiskUtils.detachHotplugDisk(serviceName(), servicePort(), node, vmId,
				diskUuid, diskTarget);
		
		return actionResponse();
	}

	private Representation actionResponse() {
		Map<String, Object> info = new HashMap<String, Object>();

		info.put("uuids", diskUuids);
		info.put("node", node);
		info.put("vm_id", vmId);

		return directTemplateRepresentation("action.ftl", info);
	}

	private QueryProcessStatus processRequest(Representation entity) {
		PersistentDiskApplication.checkEntity(entity);
		PersistentDiskApplication.checkMediaType(entity.getMediaType());

		Form form = new Form(entity);

		if (form.size() < 2) {
			return QueryProcessStatus.MISSING;
		}

		for (String input : form.getNames()) {
			if (input.equalsIgnoreCase("node")) {
				node = form.getFirstValue(input);
			} else if (input.equalsIgnoreCase("vm_id")) {
				vmId = form.getFirstValue(input);
			} else {
				return QueryProcessStatus.UNKNOW;
			}
		}

		return QueryProcessStatus.OK;
	}

	private String getDiskId() {
		Map<String, Object> attributes = getRequest().getAttributes();

		if (!attributes.containsKey("uuid")) {
			return null;
		}

		return attributes.get("uuid").toString();
	}

	private DiskAction getAction() {
		Map<String, Object> attributes = getRequest().getAttributes();
		String action = attributes.get("action").toString();

		if (action.equals("attach")) {
			return DiskAction.ATTACH;
		} else if (action.equals("detach")) {
			return DiskAction.DETACH;
		} else if (action.equals("hotattach")) {
			return DiskAction.HOT_ATTACH;
		} else if (action.equals("hotdetach")) {
			return DiskAction.HOT_DETACH;
		} else {
			return null;
		}
	}
}
