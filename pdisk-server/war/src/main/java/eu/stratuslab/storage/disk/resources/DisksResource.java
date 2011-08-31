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
import java.util.UUID;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;

public class DisksResource extends BaseResource {

    @Get("html")
    public Representation getAsHtml() {
        Map<String, Object> info = listDisks();

        return createTemplateRepresentation("html/disks.ftl", info, TEXT_HTML);
    }

    @Get("json")
    public Representation getAsJson() {
        Map<String, Object> info = listDisks();

        return createTemplateRepresentation("json/disks.ftl", info,
                APPLICATION_JSON);

    }

    private Map<String, Object> listDisks() {
        Map<String, Object> info = createInfoStructure("Disks list");

        List<Properties> diskInfoList = new LinkedList<Properties>();
        List<String> disks = zk.getDisks();

        info.put("disks", diskInfoList);

        for (String uuid : disks) {
            Properties properties = zk.getDiskProperties(getDiskZkPath(uuid));

            // List only disk of the user
            if (hasSufficientRightsToView(properties)) {
                diskInfoList.add(properties);
            }
        }

        return info;
    }

    @Post("html")
    public Representation createDiskRequestFromHtml(Representation entity) {

        Properties diskProperties = getDiskProperties(entity);

        createDisk(diskProperties);

        String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

        MESSAGES.push("Your disk has been created successfully.");
        redirectSeeOther(getBaseUrl() + "/disks/" + uuid + "/");

        return null;
    }

    @Post("json")
    public Representation createDiskRequestFromJson(Representation entity) {

        Properties diskProperties = getDiskProperties(entity);

        createDisk(diskProperties);

        String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

        setStatus(Status.SUCCESS_CREATED);

        Map<String, Object> info = new HashMap<String, Object>();
        info.put("key", "uuid");
        info.put("value", uuid);

        return createTemplateRepresentation("json/keyvalue.ftl", info,
                APPLICATION_JSON);

    }

    public Properties getDiskProperties(Representation entity) {

        PersistentDiskApplication.checkEntity(entity);
        PersistentDiskApplication.checkMediaType(entity.getMediaType());

        Properties diskProperties = processWebForm();
        List<String> errors = validateDiskProperties(diskProperties);

        // Display form again if we have error(s)
        if (errors.size() > 0) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    PersistentDiskApplication.join(errors, "\", \""));
        }

        return diskProperties;
    }

    private Properties getEmptyFormProperties() {
        Properties properties = new Properties();

        properties.put("size", "");
        properties.put("tag", "");
        properties.put("visibility", "private");

        return properties;
    }

    private Properties processWebForm() {
        Properties properties = initializeProperties();
        Representation entity = getRequest().getEntity();
        Form form = new Form(entity);

        for (String name : form.getNames()) {
            String value = form.getFirstValue(name);
            if (value != null) {
                properties.put(name, value);
            }
        }

        return properties;
    }

    private Properties initializeProperties() {
        Properties properties = getEmptyFormProperties();
        properties.put(DiskProperties.UUID_KEY, generateUUID());
        properties
                .put(DiskProperties.DISK_OWNER_KEY, getUsername(getRequest()));
        properties.put(DiskProperties.DISK_CREATION_DATE_KEY,
                PersistentDiskApplication.getDateTime());
        properties.put(DiskProperties.DISK_USERS_KEY, "0");

        return properties;
    }

    private static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    private List<String> validateDiskProperties(Properties diskProperties) {
        List<String> errors = new LinkedList<String>();

        try {
            diskVisibilityFromString(diskProperties.getProperty("visibility",
                    "None"));
        } catch (RuntimeException e) {
            errors.add("Invalid disk visibility");
        }

        try {
            String size = diskProperties.getProperty("size", "None");
            int gigabytes = Integer.parseInt(size);

            if (gigabytes < ServiceConfiguration.DISK_SIZE_MIN
                    || gigabytes > ServiceConfiguration.DISK_SIZE_MAX) {
                errors.add("Size must be an integer between "
                        + ServiceConfiguration.DISK_SIZE_MIN + " and "
                        + ServiceConfiguration.DISK_SIZE_MAX);
            }
        } catch (NumberFormatException e) {
            errors.add("Size must be a valid positive integer");
        }

        return errors;
    }

    private void createDisk(Properties properties) {
        String diskRoot = getDiskZkPath(properties.get(DiskProperties.UUID_KEY)
                .toString());

        zk.saveDiskProperties(diskRoot, properties);
        DiskUtils.createDisk(properties);
    }

}
