package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.HashMap;
import java.util.Map;

import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class VmsResource extends BaseResource {

    @Get("html")
    public Representation getAsHtml() {

        Map<String, Object> info = getVmProperties();
        return createTemplateRepresentation("html/vms.ftl", info, TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {

        Map<String, Object> info = getVmProperties();
        return createTemplateRepresentation("json/vms.ftl", info,
                APPLICATION_JSON);
    }

    private Map<String, Object> getVmProperties() {
        Map<String, Object> info = new HashMap<String, Object>();
        return info;
    }

}
