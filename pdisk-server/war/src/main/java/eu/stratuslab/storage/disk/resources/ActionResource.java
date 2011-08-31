package eu.stratuslab.storage.disk.resources;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class ActionResource extends BaseResource {

    private enum DiskAction {
        ATTACH, DETACH, HOT_ATTACH, HOT_DETACH;
    }

    private enum QueryProcessStatus {
        MISSING, UNKNOW, OK;
    }

    private List<String> diskUuids = null;
    private String node = null;
    private String vmId = null;
    private String diskTarget = null;

    @Post
    public Representation actionDispatcher(Representation entity) {
        DiskAction action = getAction();
        QueryProcessStatus processingResult = processRequest(entity);

        if (processingResult == QueryProcessStatus.MISSING) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "missing input");
        } else if (processingResult == QueryProcessStatus.UNKNOW) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "unknown input");
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
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "unknown action");
        }

    }

    private Representation attachDisk(String target) {
        String diskUuid = getDiskId();

        if (diskUuid == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "Disk UUID not provided");
        } else if (!diskExists(diskUuid)) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND,
                    "unknown disk UUID");
        } else if (target.equals(DiskProperties.DISK_TARGET_LIMIT)) {
            throw new ResourceException(Status.CLIENT_ERROR_CONFLICT,
                    "Target limit reached. Restart instance to attach new disk");
        }

        Properties diskProperties = getDiskProperties(diskUuid);
        if (!hasSufficientRightsToView(diskProperties)) {
            throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
                    "Not enough rights to attach disk");
        }

        zk.addDiskUser(node, vmId, diskUuid, target);
        diskUuids = zk.getAttachedDisk(node, vmId);

        if (!target.equals(DiskProperties.STATIC_DISK_TARGET)) {
            DiskUtils.attachHotplugDisk(serviceName(), servicePort(), node,
                    vmId, diskUuid, target);
            diskTarget = target;
        }

        return actionResponse();
    }

    private Representation detachAllDisks() {
        diskUuids = zk.getAttachedDisk(node, vmId);

        if (diskUuids == null) {
            throw new ResourceException(Status.CLIENT_ERROR_CONFLICT,
                    "VM don't have disk attached");
        }

        for (String uuid : diskUuids) {
            Properties diskProperties = getDiskProperties(uuid);
            if (!hasSufficientRightsToView(diskProperties)) {
                throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
                        "Not enough rights to detach disks");
            }
        }

        if (zk.removeDiskUser(node, vmId)) {
            return actionResponse();
        } else {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while detaching disk");
        }
    }

    private Representation detachHotpluggedDisk() {
        String diskUuid = getDiskId();
        List<String> disks = new LinkedList<String>();
        Properties diskProperties = getDiskProperties(diskUuid);

        if (!hasSufficientRightsToView(diskProperties)) {
            throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
                    "Not enough rights to detach disk");
        }

        disks.add(diskUuid);
        diskUuids = disks;

        if (diskUuid == null || !diskExists(diskUuid)) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND,
                    "Bad disk UUID");
        }

        diskTarget = zk.diskTarget(node, vmId, diskUuid);
        if (diskTarget.equals(DiskProperties.STATIC_DISK_TARGET)) {
            throw new ResourceException(Status.CLIENT_ERROR_CONFLICT,
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

        if (diskTarget != null) {
            info.put("target", diskTarget);
        }

        return directTemplateRepresentation("action.ftl", info);
    }

    private QueryProcessStatus processRequest(Representation entity) {
        RootApplication.checkEntity(entity);
        RootApplication.checkMediaType(entity.getMediaType());

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
