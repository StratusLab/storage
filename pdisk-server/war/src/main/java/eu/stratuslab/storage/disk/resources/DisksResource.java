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

import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;

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

    @Post("form:html")
    public Representation createDiskRequestFromHtml(Representation entity) {

        Properties diskProperties = getDiskProperties(entity);

        createDisk(diskProperties);

        String uuid = diskProperties.getProperty(DiskProperties.UUID_KEY);

        MESSAGES.push("Your disk has been created successfully.");
        redirectSeeOther(getBaseUrl() + "/disks/" + uuid + "/");

        return null;
    }

    @Post("form:json")
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

    private Map<String, Object> listDisks() {
        Map<String, Object> info = createInfoStructure("Disks list");

        addCreateFormDefaults(info);

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

    private void addCreateFormDefaults(Map<String, Object> info) {
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put("size", "1");
        defaults.put("tag", "");
        defaults.put("visibility", DiskVisibility.PRIVATE.toString());

        info.put("values", defaults);

        List<String> visibilities = new LinkedList<String>();
        for (DiskVisibility visibility : DiskVisibility.values()) {
            visibilities.add(visibility.toString());
        }

        info.put("visibilities", visibilities);
    }

    private Properties getDiskProperties(Representation entity) {

        MiscUtils.checkForNullEntity(entity);

        Properties diskProperties = processWebForm();
        List<String> errors = validateDiskProperties(diskProperties);

        // Display form again if we have error(s)
        if (errors.size() > 0) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    MiscUtils.join(errors, "\", \""));
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
                MiscUtils.getTimestamp());
        properties.put(DiskProperties.DISK_USERS_KEY, "0");

        return properties;
    }

    private static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    private List<String> validateDiskProperties(Properties diskProperties) {
        List<String> errors = new LinkedList<String>();

        String visibility = "none";
        try {
            visibility = diskProperties.getProperty("visibility", "none");
            DiskVisibility.valueOfIgnoreCase(visibility);
        } catch (IllegalArgumentException e) {
            errors.add("invalid disk visibility: " + visibility);
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
        registerDisk(properties);
        DiskUtils.createDisk(properties);
    }

    private void registerDisk(Properties properties) {
        String uuid = properties.get(DiskProperties.UUID_KEY).toString();
        String diskRoot = getDiskZkPath(uuid);
        zk.saveDiskProperties(diskRoot, properties);
    }

}
