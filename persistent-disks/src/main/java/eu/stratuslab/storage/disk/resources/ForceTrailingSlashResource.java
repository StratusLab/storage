/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.resources;

import static org.restlet.data.MediaType.TEXT_PLAIN;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

public class ForceTrailingSlashResource extends ServerResource {

    @Get
    public Representation redirectGet() {
        setRedirectRef();
        return new StringRepresentation("dummy representation", TEXT_PLAIN);
    }

    @Post
    public Representation redirectPost(Representation entity) {
        setRedirectRef();
        return new StringRepresentation("dummy representation", TEXT_PLAIN);
    }

    @Put
    public Representation redirectPut(Representation entity) {
        setRedirectRef();
        return new StringRepresentation("dummy representation", TEXT_PLAIN);
    }

    @Delete
    public void redirectDelete() {
        setRedirectRef();
    }

    private void setRedirectRef() {

        Request request = getRequest();
        Reference ref = request.getResourceRef();
        String sref = ref.toString() + "/";
        Reference redirect = new Reference(sref);

        Response response = getResponse();
        response.redirectPermanent(redirect);
    }

}
