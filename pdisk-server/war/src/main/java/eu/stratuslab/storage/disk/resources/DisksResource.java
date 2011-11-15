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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import eu.stratuslab.storage.disk.utils.DiskProperties;

public class DisksResource extends DiskBaseResource {

    @Get("html")
    public Representation getAsHtml() {

        getLogger().info("DisksResource getAsHtml");

        Map<String, Object> info = listDisks();

        return createTemplateRepresentation("html/disks.ftl", info, TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {

        getLogger().info("DisksResource getAsJson");

        Map<String, Object> info = listDisks();

        return createTemplateRepresentation("json/disks.ftl", info,
                APPLICATION_JSON);

    }

    @Post("form:html")
    public Representation createDiskRequestFromHtml(Representation entity) {

        getLogger().info("DisksResource createDiskRequestFromHtml");

        Properties diskProperties = getDiskProperties(new Form(entity));

        validateDiskProperties(diskProperties);
        
        createDisk(diskProperties);

        String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

        redirectSeeOther(getBaseUrl() + "/disks/" + uuid + "/");

        return null;
    }

    @Post("form:json")
    public Representation createDiskRequestFromJson(Representation entity) {

        getLogger().info("DisksResource createDiskRequestFromJson");

        Properties diskProperties = getDiskProperties(new Form(entity));

        validateDiskProperties(diskProperties);
        
        createDisk(diskProperties);

        String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

        setStatus(Status.SUCCESS_CREATED);

        Map<String, Object> info = new HashMap<String, Object>();
        info.put("key", DiskProperties.UUID_KEY);
        info.put("value", uuid);

        return createTemplateRepresentation("json/keyvalue.ftl", info,
                APPLICATION_JSON);

    }

    private Map<String, Object> listDisks() {
        Map<String, Object> info = createInfoStructure("Disks list");

        addCreateFormDefaults(info);

        List<Properties> diskInfoList = new LinkedList<Properties>();
        info.put("disks", diskInfoList);

        List<String> disks = zk.getDisks();
        for (String uuid : disks) {
            Properties properties = zk.getDiskProperties(uuid);

            // List only disk of the user
            if (hasSufficientRightsToView(properties)) {
                diskInfoList.add(properties);
            }
        }

        return info;
    }

    private void addCreateFormDefaults(Map<String, Object> info) {
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put(DiskProperties.DISK_SIZE_KEY, "1");
        defaults.put(DiskProperties.DISK_TAG_KEY, "");
        defaults.put(DiskProperties.DISK_VISIBILITY_KEY, DiskVisibility.PRIVATE.toString());

        info.put("values", defaults);

        List<String> visibilities = new LinkedList<String>();
        for (DiskVisibility visibility : DiskVisibility.values()) {
            visibilities.add(visibility.toString());
        }

        info.put("visibilities", visibilities);
    }

}
