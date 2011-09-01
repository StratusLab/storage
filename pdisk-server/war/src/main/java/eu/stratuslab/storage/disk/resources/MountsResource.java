package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.APPLICATION_JSON;

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

import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;

public class MountsResource extends BaseResource {

    private enum DiskAction {
        ATTACH, DETACH, HOTATTACH, HOTDETACH;

        public static DiskAction valueOfIgnoreCase(String value) {
            return valueOf(value.toUpperCase());
        }

    }

    private List<String> diskUuids = null;
    private String node = null;
    private String vmId = null;
    private String diskTarget = null;

    @Post("json")
    public Representation actionDispatcher(Representation entity) {

        DiskAction action = getAction();
        extractNodeAndVmId(entity);

        switch (action) {
        case ATTACH:
            return attachDisk(DiskProperties.STATIC_DISK_TARGET);
        case HOTATTACH:
            return attachDisk(zk.nextHotpluggedDiskTarget(node, vmId));
        case DETACH:
            return detachAllDisks();
        case HOTDETACH:
            return detachHotpluggedDisk();
        default:
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

        return createTemplateRepresentation("json/action.ftl", info,
                APPLICATION_JSON);
    }

    private void extractNodeAndVmId(Representation entity) {

        MiscUtils.checkForNullEntity(entity);
        MiscUtils.checkForWebForm(entity.getMediaType());

        Form form = new Form(entity);

        if (form.size() != 2) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "form has incorrect number of values; expected 2, received "
                            + form.size());
        }

        node = form.getFirstValue("node");
        if (node == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "missing node attribute");
        }

        vmId = form.getFirstValue("vm_id");
        if (vmId == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "missing vm_id attribute");
        }

    }

    private String getDiskId() {
        Map<String, Object> attributes = getRequest().getAttributes();
        Object diskId = attributes.get("uuid");
        return (diskId != null) ? diskId.toString() : null;
    }

    private DiskAction getAction() {
        Map<String, Object> attributes = getRequest().getAttributes();
        Object action = attributes.get("action");

        if (action == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "null action is not permitted");
        }

        try {
            return DiskAction.valueOfIgnoreCase(action.toString());
        } catch (IllegalArgumentException e) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "invalid action: " + action);
        }

    }
}
