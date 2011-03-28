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

import static org.restlet.data.MediaType.APPLICATION_WWW_FORM;
import static org.restlet.data.MediaType.MULTIPART_FORM_DATA;
import static org.restlet.data.MediaType.TEXT_HTML;
import static org.restlet.data.MediaType.TEXT_PLAIN;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.restlet.data.LocalReference;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class DisksResource extends ServerResource {

    @Post
    public Representation createDisk(Representation entity)
            throws ResourceException {

        if (entity == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "post with null entity");
        }

        MediaType mediaType = entity.getMediaType();

        Properties diskProperties = null;
        if (MULTIPART_FORM_DATA.equals(mediaType, true)) {
            diskProperties = processMultipartForm();
        } else if (APPLICATION_WWW_FORM.equals(mediaType, true)) {
            diskProperties = processWWWForm();
        } else {
            throw new ResourceException(
                    Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE, mediaType
                            .getName());
        }

        validateDiskProperties(diskProperties);

        initializeDisk(diskProperties);

        String uuid = diskProperties.getProperty("uuid");

        setStatus(Status.SUCCESS_CREATED);
        Representation rep = new StringRepresentation("disk created: " + uuid,
                TEXT_PLAIN);

        String diskRelativeUrl = "/disks/" + uuid;
        rep.setLocationRef(getRequest().getResourceRef().getIdentifier()
                + diskRelativeUrl);

        return rep;

    }

    @Get("txt")
    public Representation toText() {
        Map<String, String> links = listDisks();
        String contents = linksToText(links);
        return new StringRepresentation(contents, TEXT_PLAIN);
    }

    @Get("html")
    public Representation toHtml() {
        Map<String, String> links = listDisks();
        return linksToHtml(links);
    }

    private static Properties processMultipartForm() {
        Properties properties = initializeProperties();
        return properties;
    }

    private static Properties processWWWForm() {
        Properties properties = initializeProperties();
        return properties;
    }

    private static Properties initializeProperties() {
        Properties properties = new Properties();
        String uuid = UUID.randomUUID().toString();
        properties.put("uuid", uuid);
        return properties;
    }

    private static void validateDiskProperties(Properties diskProperties)
            throws ResourceException {

        // FIXME: Add real implementation.
    }

    private static void initializeDisk(Properties properties)
            throws ResourceException {

        String uuid = properties.getProperty("uuid");

        File diskLocation = new File(PersistentDiskApplication.diskStore, uuid);
        File propertiesFile = new File(diskLocation, "disk.properties");
        File contentsFile = new File(diskLocation, "contents");

        if (diskLocation.exists()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "disk already exists: " + uuid);
        }

        if (!diskLocation.mkdirs()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot write to disk store: " + diskLocation);
        }

        if (!diskLocation.canWrite()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot write to disk store: " + diskLocation);
        }

        storeDiskProperties(properties, propertiesFile);

        initializeDiskContents(properties, contentsFile);

    }

    private static void storeDiskProperties(Properties properties, File location)
            throws ResourceException {

        Writer writer = null;
        try {

            writer = new FileWriter(location);
            properties.store(writer, "comment");

        } catch (IOException e) {

        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException consumed) {
                    // TODO: Log this error.
                }
            }
        }

    }

    private static void initializeDiskContents(Properties properties,
            File location) throws ResourceException {

        try {
            if (!location.createNewFile()) {
                throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                        "contents file already exists: " + location);
            }
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot create contents file: " + location);
        }

    }

    private String linksToText(Map<String, String> links) {

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : links.entrySet()) {
            sb.append(entry.getKey() + " -> " + entry.getValue() + "\n");
        }

        return sb.toString();
    }

    private Representation linksToHtml(Map<String, String> links) {

        LocalReference ref = LocalReference.createClapReference("/disks.ftl");
        Representation disksFtl = new ClientResource(ref).get();

        Map<String, Object> infoTree = new HashMap<String, Object>();
        infoTree.put("links", links);

        return new TemplateRepresentation(disksFtl, infoTree,
                MediaType.TEXT_HTML);

    }

    private Map<String, String> listDisks() {

        File store = PersistentDiskApplication.diskStore;

        Map<String, String> links = new HashMap<String, String>();

        String[] files = store.list();
        if (files != null) {
            for (String name : files) {
                String link = name;
                links.put(name, link);
            }
        }

        return links;
    }
}
