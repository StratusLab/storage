package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.Map;

import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class HomeResource extends BaseResource {
	@Get("html")
	public Representation toHtml() {
		
		for (String h : getRequest().getAttributes().keySet()) {
			LOGGER.info("[HTTP HEADER] " + h + ": " + getRequest().getAttributes().get(h));
		}
		
		for (String c : getCookies().getNames()) {
			LOGGER.info("[COOKIE] " + c + ": " + getCookies().getFirstValue(c));
		}
		
		Map<String, Object> infos = createInfoStructure("Home");
		return createTemplateRepresentation("html/home.ftl", infos, TEXT_HTML);
	}
}
