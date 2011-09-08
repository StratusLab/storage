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

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class DiskResource extends BaseResource {

    @Get("html")
    public Representation getAsHtml() {

        Map<String, Object> info = listDiskProperties();

        addDiskUserHeader();

        return createTemplateRepresentation("html/disk.ftl", info, TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {

        Map<String, Object> info = listDiskProperties();

        addDiskUserHeader();

        return createTemplateRepresentation("json/disk.ftl", info,
                APPLICATION_JSON);
    }

    public Map<String, Object> listDiskProperties() {
        Map<String, Object> infos = createInfoStructure("Disk info");

        if (!diskExists(getDiskId())) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "disk ("
                    + getDiskId() + ") does not exist");
        }

        Properties diskProperties = getDiskProperties();

        if (!hasSufficientRightsToView(diskProperties)) {
            throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
                    "insuffient access rights to view disk (" + getDiskId()
                            + ")");
        }

        infos.put("properties", diskProperties);
        infos.put("url", getCurrentUrl());
        infos.put("can_delete", hasSufficientRightsToDelete(diskProperties));

        return infos;

    }

    private Properties getDiskProperties() {
        return getDiskProperties(getDiskId());
    }

    private void addDiskUserHeader() {
        Form diskUserHeaders = (Form) getResponse().getAttributes().get(
                "org.restlet.http.headers");

        if (diskUserHeaders == null) {
            diskUserHeaders = new Form();
            getResponse().getAttributes().put("org.restlet.http.headers",
                    diskUserHeaders);
        }

        diskUserHeaders.add("X-DiskUser-Limit",
                String.valueOf(RootApplication.CONFIGURATION.USERS_PER_DISK));
        diskUserHeaders.add("X-DiskUser-Remaining",
                String.valueOf(zk.remainingFreeUser(getDiskZkPath())));
    }

    @Delete("html")
    public Representation deleteDiskAsHtml() {

        processDeleteDiskRequest();

        MESSAGES.push("Your disk have been deleted successfully");
        redirectSeeOther(getBaseUrl() + "/disks/");

        Map<String, Object> info = createInfoStructure("redirect");
        return createTemplateRepresentation("html/redirect.ftl", info,
                TEXT_HTML);
    }

    @Delete("json")
    public Representation deleteDiskAsJson() {

        processDeleteDiskRequest();

        Map<String, Object> info = new HashMap<String, Object>();
        info.put("key", "uuid");
        info.put("value", getDiskId());

        return createTemplateRepresentation("json/keyvalue.ftl", info,
                APPLICATION_JSON);
    }

    public void processDeleteDiskRequest() {

        String diskId = getDiskId();

        if (!diskExists(diskId)) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "disk ("
                    + diskId + ") does not exist");
        }

        Properties diskProperties = getDiskProperties();

        if (!hasSufficientRightsToDelete(diskProperties)) {
            throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
                    "insufficient rights to delete disk (" + diskId + ")");
        }

        if (zk.getDiskUsersNo(getDiskZkPath()) > 0) {
            throw new ResourceException(Status.CLIENT_ERROR_CONFLICT, "disk ("
                    + diskId + ") is in use and can't be deleted");
        }

        deleteDisk();
    }

    private void deleteDisk() {
        zk.deleteRecursively(getDiskZkPath());
        DiskUtils.removeDisk(getDiskId());
    }

    private String getDiskZkPath() {
        return getDiskZkPath(getDiskId());
    }

    private String getDiskId() {
        Map<String, Object> attributes = getRequest().getAttributes();

        return attributes.get("uuid").toString();
    }

}
