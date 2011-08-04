package eu.stratuslab.storage.disk.resources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class ActionResource extends BaseResource {

	private enum DiskAction {
		ATTACH, DETACH;
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
			return attachDisk();
		} else if (action == DiskAction.DETACH) {
			return detachDisk();
		} else {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"Requested action does not exist");
		}

	}

	private Representation attachDisk() {
		String diskUuid = getDiskId();
		
		if (!diskExists(diskUuid)) {
			return respondError(Status.CLIENT_ERROR_NOT_FOUND, "Bad disk UUID");
		}
		
		zk.addDiskUser(node, vmId, diskUuid);
		diskUuids = zk.getAttachedDisk(node, vmId);
		
		return actionResponse();
	}

	private Representation detachDisk() {
		diskUuids = zk.getAttachedDisk(node, vmId);
		
		if (zk.removeDiskUser(node, vmId)) {
			return actionResponse();
		} else {
			return respondError(Status.CLIENT_ERROR_BAD_REQUEST,
					"VM don't have disk attached");
		}
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

		return attributes.get("uuid").toString();
	}

	private DiskAction getAction() {
		Map<String, Object> attributes = getRequest().getAttributes();
		String action = attributes.get("action").toString();

		if (action.equals("attach")) {
			return DiskAction.ATTACH;
		} else if (action.equals("detach")) {
			return DiskAction.DETACH;
		} else {
			return null;
		}
	}
}
