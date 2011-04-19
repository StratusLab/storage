package eu.stratuslab.storage.disk.resources;

import org.restlet.data.MediaType;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class CreateResource extends BaseResource {

    @Get("html")
    public Representation toHtml() {
        Representation tpl = templateRepresentation("/html/upload.ftl");
        return new TemplateRepresentation(tpl, MediaType.TEXT_HTML);
    }

}
