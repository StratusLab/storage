package eu.stratuslab.storage.disk.resources;

import java.util.logging.Logger;

import org.restlet.data.LocalReference;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ServerResource;

public class BaseResource extends ServerResource {

    protected static final Logger LOGGER = Logger.getLogger("org.restlet");

    protected Representation templateRepresentation(String tpl) {
        LocalReference ref = LocalReference.createClapReference(tpl);
        return new ClientResource(ref).get();
    }

}
