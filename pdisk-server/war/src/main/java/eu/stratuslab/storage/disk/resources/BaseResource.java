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

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_HTML;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.restlet.Request;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.Messages;
import freemarker.template.Configuration;

public class BaseResource extends ServerResource {

    protected static final DiskProperties zk = new DiskProperties();
    protected static final Logger LOGGER = Logger.getLogger("org.restlet");
    protected static final Messages MESSAGES = new Messages();

    public enum DiskVisibility {
        PRIVATE,
        // RESTRICTED,
        PUBLIC,
    }

    private Configuration getFreeMarkerConfiguration() {
        return ((RootApplication) getApplication())
                .getFreeMarkerConfiguration();
    }

    protected TemplateRepresentation createTemplateRepresentation(String tpl,
            Map<String, Object> info, MediaType mediaType) {

        Configuration freeMarkerConfig = getFreeMarkerConfiguration();
        return createTemplateRepresentation(freeMarkerConfig, tpl, info,
                mediaType);

    }

    public static TemplateRepresentation createTemplateRepresentation(
            Configuration freeMarkerConfig, String tpl,
            Map<String, Object> info, MediaType mediaType) {

        return new TemplateRepresentation(tpl, freeMarkerConfig, info,
                mediaType);
    }

    protected TemplateRepresentation directTemplateRepresentation(String tpl,
            Map<String, Object> info) {
        return createTemplateRepresentation(getTemplateType() + "/" + tpl,
                info, getReturnedMediaType());
    }

    protected Map<String, Object> createInfoStructure(String title) {

        return createInfoStructure(title, getRequest(), MESSAGES.pop(),
                getBaseUrl());
    }

    public static Map<String, Object> createInfoStructure(String title,
            Request request, String msg, String baseUrl) {

        Map<String, Object> info = new HashMap<String, Object>();

        // Add the title if appropriate.
        if (title != null && !"".equals(title)) {
            info.put("title", title);
        }

        // Add the standard base URL declaration.
        info.put("baseurl", baseUrl);

        // Add user name information
        info.put("username", getUsername(request));

        // Display message if available
        info.put("success", msg);

        return info;
    }

    public static String getUsername(Request request) {
        ChallengeResponse cr = request.getChallengeResponse();
        return (cr == null) ? "UNKNOWN" : cr.getIdentifier();
    }

    protected String getBaseUrl() {
        return getRequest().getRootRef().toString();
    }

    public static String getBaseUrl(Request request) {
        return request.getRootRef().toString();
    }

    protected String getCurrentUrl() {
        return getCurrentUrlWithQueryString().replaceAll("\\?.*", "");
    }

    protected String getCurrentUrlWithQueryString() {
        return getRequest().getResourceRef().toString();
    }

    protected String getQueryString() {
        return getRequest().getResourceRef().getQuery();
    }

    protected Boolean hasQueryString(String key) {
        String queryString = getQueryString();
        return (queryString != null && queryString.equals(key));
    }

    protected static String getDiskZkPath(String uuid) {
        return RootApplication.CONFIGURATION.ZK_DISKS_PATH + "/"
                + uuid;
    }

    protected Boolean diskExists(String uuid) {
        return zk.pathExists(getDiskZkPath(uuid));
    }

    protected Boolean hasSufficientRightsToView(Properties properties) {
        // Is disk owner or service user
        if (properties.get(DiskProperties.DISK_OWNER_KEY).toString()
                .equals(getUsername(getRequest()))
                || RootApplication.CONFIGURATION.CLOUD_SERVICE_USER
                        .equals(getUsername(getRequest()))) {
            return true;
        }

        DiskVisibility currentVisibility = diskVisibilityFromString(properties
                .get(DiskProperties.DISK_VISIBILITY_KEY).toString());

        // Is disk public
        if (currentVisibility.equals(DiskVisibility.PUBLIC)) {
            return true;
        }

        return false;
    }

    protected Boolean hasSufficientRightsToDelete(Properties properties) {
        // Need to be the owner to delete the disk
        return properties.get(DiskProperties.DISK_OWNER_KEY).toString()
                .equals(getUsername(getRequest()));
    }

    protected String diskVisibilityToString(DiskVisibility visibility) {
        switch (visibility) {
        case PUBLIC:
            return "public";
            // case RESTRICTED:
            // return "restricted";
        case PRIVATE:
            return "private";
        default:
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "Invalide disk visibility");
        }
    }

    protected DiskVisibility diskVisibilityFromString(String visibility) {
        if ("public".equals(visibility)) {
            return DiskVisibility.PUBLIC;
        } else if ("private".equals(visibility)) {
            return DiskVisibility.PRIVATE;
            // } else if ("restricted".equals(visibility)) {
            // return DiskVisibility.RESTRICTED;
        } else {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "Invalide disk visibility: " + visibility);
        }
    }

    protected Boolean useAPI() {
        return getRequest().getResourceRef().getPath().startsWith("/api/");
    }

    protected String getTemplateType() {
        if (useAPI()) {
            return "json";
        } else {
            return "html";
        }
    }

    protected MediaType getReturnedMediaType() {
        if (useAPI()) {
            return APPLICATION_JSON;
        } else {
            return TEXT_HTML;
        }
    }

    protected String serviceName() {
        return getRequest().getHostRef().getHostDomain();
    }

    protected int servicePort() {
        return getRequest().getHostRef().getHostPort();
    }

    protected Properties getDiskProperties(String uuid) {
        return zk.getDiskProperties(getDiskZkPath(uuid));
    }

    public static DiskProperties getZooKeeper() {
        return zk;
    }

}
