package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.restlet.Request;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

public class VmResource extends BaseResource {

    private String mountId = null;
    private String vmId = null;
    private String node = null;

    @Override
    public void doInit() {

        Map<String, Object> attributes = getRequest().getAttributes();

        Object mountIdValue = attributes.get("mountid");
        if (mountIdValue == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "mount ID cannot be null");

        }
        mountId = mountIdValue.toString();

        String[] fields = mountId.split("-", 2);
        if (fields.length != 2) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "malformed mount ID");
        }

        vmId = fields[0];
        if ("".equals(vmId)) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "illegal VM identifier");
        }

        node = fields[1];
        if ("".equals(node)) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "illegal node name");
        }

    }

    @Get("html")
    public Representation getAsHtml() {

        Map<String, Object> info = getVmProperties();
        return createTemplateRepresentation("html/vm.ftl", info, TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {

        Map<String, Object> info = getVmProperties();
        return createTemplateRepresentation("json/vm.ftl", info,
                APPLICATION_JSON);
    }

    @Delete
    public void deleteAllMounts() {
        detachAllDisks();
    }

    private Map<String, Object> getVmProperties() {

        Request request = getRequest();

        Map<String, Object> info = createInfoStructure("Virtual Machine",
                request, "", BaseResource.getBaseUrl(request));

        info.put("vmId", vmId);

        return info;
    }

    private void detachAllDisks() {
        List<String> diskUuids = zk.getAttachedDisk(node, vmId);

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

        if (!zk.removeDiskUser(node, vmId)) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while detaching disk");
        }
    }

}
