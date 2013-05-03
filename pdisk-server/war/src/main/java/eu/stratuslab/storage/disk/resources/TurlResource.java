package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.Map;

import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.persistence.Disk;

public class TurlResource extends BaseResource {

    private String uuid = null;
    private String turl = null;

    @Override
    public void doInit() {

        Map<String, Object> attributes = getRequest().getAttributes();

        Object diskIdValue = attributes.get("uuid");
        if (diskIdValue == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "missing UUID value");
        }

        uuid = diskIdValue.toString();

        Disk disk = Disk.load(uuid);
        if (disk == null) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND,
                    "Unknown disk: " + uuid);
        }

        turl = DiskUtils.getTurl(uuid);

    }

    @Get("html")
    public Representation getAsHtml() {
        return createTemplateRepresentation("html/turl.ftl", getInfoMap(),
                TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {
        return createTemplateRepresentation("json/turl.ftl", getInfoMap(),
                APPLICATION_JSON);
    }

    private Map<String, Object> getInfoMap() {
        Map<String, Object> info = this.createInfoStructure("Transport URL");
        info.put("uuid", uuid);
        info.put("turl", turl);
        return info;
    }
}
