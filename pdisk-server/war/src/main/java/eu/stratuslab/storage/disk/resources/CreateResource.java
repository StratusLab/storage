package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.Map;

import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class CreateResource extends BaseResource {

    @Get("html")
    public Representation toHtml() {
    	Map<String, Object> infos = createInfoStructure("Create a disk");
		return createTemplateRepresentation("html/upload.ftl", infos, TEXT_HTML);
    }
    
}
