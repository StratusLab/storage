package eu.stratuslab.storage.disk.resources;

import eu.stratuslab.storage.disk.backend.BackEndStorage;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.Instance;
import eu.stratuslab.storage.persistence.Mount;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

public class MountsResource extends BaseResource {

    private String node = null;
    private String vmId = null;
    private boolean registerOnly = false;
    private Disk disk = null;
    private Instance instance = null;
    private String mountId = null;

    @Override
    public void doInit() {

        Map<String, Object> attributes = getRequest().getAttributes();

        Object diskIdValue = attributes.get("uuid");
        if (diskIdValue == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "missing UUID value");
        }
        String uuid = diskIdValue.toString();
        disk = Disk.load(uuid);
        if (disk == null) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Unknown disk: " + uuid);
        }

    }

    @Get("html")
    public Representation getAsHtml() {

        Map<String, Object> info = getMounts();

        return createTemplateRepresentation("html/mounts.ftl", info, TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {

        Map<String, Object> info = getMounts();

        return createTemplateRepresentation("json/mounts.ftl", info, APPLICATION_JSON);
    }

    @Post("form:html")
    public Representation createDiskRequestFromHtml(Representation entity) {

        mountDisk(entity);

        redirectSeeOther(getBaseUrl() + "disks/" + disk.getUuid() + "/mounts/" + mountId);

        return null;
    }

    @Post("form:json")
    public Representation mountDiskAsJson(Representation entity) {
        return mountDisk(entity);
    }

    // Note: Returned representation will always be JSON. This should be ignored
    // when HTML is requested.
    private Representation mountDisk(Representation entity) {

        // Validates form values as well. The node and vmId variables will NOT
        // be null after this call.
        extractFormValues(entity);

        instance = Instance.load(vmId);
        if (instance == null) {
            instance = new Instance(vmId, getUsername(getRequest()));
        }

        String target = registerOnly ? Disk.STATIC_DISK_TARGET : instance.nextDiskTarget(vmId);

        getLogger().info("DiskResource mountDisk: " + registerOnly + " " + disk
                        .getUuid() + ", " + node + ", " + vmId + ", " + target);

        try {
            return attachDisk(target);
        } catch (RuntimeException e) {
            try {
                instance.getMounts().remove(target);
            } catch (RuntimeException e2) {
                // it's ok
            }
            throw e;
        }

    }

    private Map<String, Object> getMounts() {
        Map<String, Object> info = this.createInfoStructure("Mount Information");
        info.put("uuid", disk.getUuid());
        info.put("mounts", Mount.getMounts(disk.getUuid()));
        return info;
    }

    private Representation actionResponse(List<String> diskUuids, String diskTarget) {
        Map<String, Object> info = new HashMap<String, Object>();

        info.put("uuids", diskUuids);
        info.put("node", node);
        info.put("vm_id", vmId);

        if (diskTarget != null) {
            info.put("target", diskTarget);
        }

        return createTemplateRepresentation("json/action.ftl", info, APPLICATION_JSON);
    }

    private Representation attachDisk(String target) {

        if (!hasSufficientRightsToView(disk)) {
            throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN, "Not enough rights to attach disk");
        }

        if (Mount.load(instance, disk) != null) {
            throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
                    "Mount already exists for: " + instance.getVmId() + " and " + disk.getUuid());
        }

        getLogger().info("attachDisk: " + node + " " + vmId + " " + disk.getUuid() + " " + target);

        if (!target.equals(Disk.STATIC_DISK_TARGET)) {
            getLogger().info("hotPlugDisk: " + node + " " + vmId + " " + disk.getUuid() + " " + target);
            BackEndStorage backEndStorage = new BackEndStorage();
            DiskUtils.attachHotplugDisk(getServiceEndpoint(), node, vmId, disk.getUuid(), target,
                    backEndStorage.getTurl(disk.getUuid()));
        }

        // Add this metadata only AFTER the device has been successfully added.
        instance.setNode(node);

        disk.store();
        instance.store();


        Mount mount = new Mount(instance, disk);
        mount.setDevice(target);

        disk.getMounts().put(vmId, mount);
        instance.getMounts().put(disk.getUuid(), mount);

        mount.store();
        mountId = mount.getId();

        List<String> diskIds = new LinkedList<String>();
        diskIds.add(disk.getUuid());

        return actionResponse(diskIds, target);
    }

    private void extractFormValues(Representation entity) {

        MiscUtils.checkForNullEntity(entity);

        Form form = new Form(entity);

        node = form.getFirstValue("node");
        if (node == null || "".equals(node)) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "missing node attribute");
        }

        vmId = form.getFirstValue("vm_id");
        if (vmId == null || "".equals(vmId)) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "missing vm_id attribute");
        }

        Object value = form.getFirstValue("register_only");
        registerOnly = (value != null && "true".equalsIgnoreCase(value.toString()));

    }
}
