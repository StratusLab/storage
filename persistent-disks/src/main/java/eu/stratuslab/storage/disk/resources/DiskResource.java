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

import static org.restlet.data.MediaType.TEXT_HTML;
import static org.restlet.data.MediaType.TEXT_PLAIN;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.restlet.Request;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class DiskResource extends BaseResource {

    @Get("txt")
    public Representation toText() {
        Representation tpl = templateRepresentation("/text/disk.ftl");

        Map<String, Object> infoTree = new HashMap<String, Object>();
        infoTree.put("properties", loadProperties());

        return new TemplateRepresentation(tpl, infoTree, TEXT_PLAIN);
    }

    @Get("html")
    public Representation toHtml() {
        Representation tpl = templateRepresentation("/html/disk.ftl");

        Map<String, Object> infoTree = new HashMap<String, Object>();
        infoTree.put("properties", loadProperties());

        return new TemplateRepresentation(tpl, infoTree, TEXT_HTML);
    }

    @Delete
    public void removeDisk() {

        String uuid = getDiskId();

        File diskLocation = new File(PersistentDiskApplication.diskStore, uuid);
        File propertiesFile = new File(diskLocation, "disk.properties");
        File contentsFile = new File(diskLocation, "contents");

        if (!contentsFile.delete()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot delete " + contentsFile);
        }

        if (!propertiesFile.delete()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot delete " + propertiesFile);
        }

        if (!diskLocation.delete()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot delete " + diskLocation);
        }
    }

    private Properties loadProperties() {

        Reader reader = null;
        File propertyFile = getDiskPropertiesFile();
        Properties properties = new Properties();
        try {

            reader = new FileReader(propertyFile);
            properties.load(reader);

        } catch (IOException e) {

            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "cannot read properties file: " + propertyFile);

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // TODO: Log this.
                }
            }
        }

        return properties;
    }

    private String getDiskId() {

        Request request = getRequest();

        Map<String, Object> attributes = request.getAttributes();

        return attributes.get("uuid").toString();
    }

    private File getDiskPropertiesFile() {
        String uuid = getDiskId();
        return new File(PersistentDiskApplication.diskStore, uuid
                + "/disk.properties");
    }

}
