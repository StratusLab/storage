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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import eu.stratuslab.storage.disk.utils.MiscUtils;

public class DiskBaseResource extends BaseResource {

	protected Properties getExistingProperties() {
		return zk.getDiskProperties(getDiskId());
	}
	
    protected Properties getDiskProperties(Form form) {

        Properties diskProperties = processWebFormWithDefaults(form);

        return diskProperties;
    }

    protected Properties getDiskProperties(Form form, Properties initialProperties) {

        Properties diskProperties = processWebForm(initialProperties, form);

        return diskProperties;
    }

    private Properties getEmptyFormProperties() {
        Properties properties = new Properties();

        properties.put(DiskProperties.DISK_SIZE_KEY, "");
        properties.put(DiskProperties.DISK_TAG_KEY, "");
        properties.put(DiskProperties.DISK_VISIBILITY_KEY, "private");
        properties.put(DiskProperties.DISK_READ_ONLY_KEY, false);
        properties.put(DiskProperties.DISK_COW_BASE_KEY, false);

        return properties;
    }

    private Properties processWebFormWithDefaults(Form form) {
        Properties initialProperties = initializeProperties();
        return processWebForm(initialProperties, form);
    }

    protected Properties processWebForm(Form form) {
    	return processWebForm(new Properties(), form);
    }
    
    private Properties processWebForm(Properties initialProperties, Form form) {
    	Properties properties = initialProperties;

        for (String name : form.getNames()) {
            String value = form.getFirstValue(name);
            if (value != null) {
                properties.put(name, value);
            }
        }

        return properties;
    }

    protected Properties initializeProperties() {
        Properties properties = getEmptyFormProperties();
        properties.put(DiskProperties.UUID_KEY, DiskUtils.generateUUID());
        properties
                .put(DiskProperties.DISK_OWNER_KEY, getUsername(getRequest()));
        properties.put(DiskProperties.DISK_CREATION_DATE_KEY,
                MiscUtils.getTimestamp());
        properties.put(DiskProperties.DISK_USERS_KEY, "0");

        return properties;
    }

    protected void validateDiskProperties(Properties diskProperties) {
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

        if (errors.size() > 0) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    MiscUtils.join(errors, "\", \""));
        }
    }

    protected void registerDisk(Properties properties) {
        zk.saveDiskProperties(properties);
    }

    protected Properties updateDisk(Properties properties) {
        String uuid = properties.get(DiskProperties.UUID_KEY).toString();
        zk.updateDiskProperties(uuid, properties);
        return zk.getDiskProperties(uuid);
    }

    protected int incrementUserCount(String uuid) {
        return zk.incrementUserCount(uuid);
    }

	protected void checkExistance() {
		if (!zk.diskExists(getDiskId())) {
	        throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "disk ("
	                + getDiskId() + ") does not exist");
	    }
	}

	protected String getDiskId() {
	    Map<String, Object> attributes = getRequest().getAttributes();
	
	    return attributes.get("uuid").toString();
	}

	protected void createDisk(Properties properties) {
	    DiskUtils.createDisk(properties);
	}

	protected void checkIsSuper() {
		if (!isSuperUser(getUsername(getRequest()))) {
			throw (new ResourceException(Status.CLIENT_ERROR_FORBIDDEN,
					"Only super user can perform this operation"));
		}
	
	}

}
