package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.ArrayList;
import java.util.Map;

import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class CreateResource extends BaseResource {

    @Get
    public Representation toHtml() {
    	Map<String, Object> infos = createInfoStructure("Create a disk");
    	
    	// Add disk possible visibilities
    	ArrayList<String> visibilities = new ArrayList<String>();
    	for (DiskVisibility v : DiskVisibility.values()) {
    		visibilities.add(diskVisibilityToString(v));
    	}
    	
    	infos.put("visibilities", visibilities);
    	
		return createTemplateRepresentation("html/create.ftl", infos, TEXT_HTML);
    }
    
}
