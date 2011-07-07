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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import freemarker.template.Configuration;

public class BaseResource extends ServerResource {

	protected static final DiskProperties zk = new DiskProperties();
	private String username = "";

	public enum DiskVisibility {
		PRIVATE,
		// RESTRICTED,
		PUBLIC,
	}

	private Configuration getFreeMarkerConfiguration() {
		return ((PersistentDiskApplication) getApplication())
				.getFreeMarkerConfiguration();
	}

	protected TemplateRepresentation createTemplateRepresentation(String tpl,
			Map<String, Object> info, MediaType mediaType) {

		Configuration freeMarkerConfig = getFreeMarkerConfiguration();

		return new TemplateRepresentation(tpl, freeMarkerConfig, info,
				mediaType);
	}

	protected Map<String, Object> createInfoStructure(String title) {
		Map<String, Object> info = new HashMap<String, Object>();

		// Add the standard base URL declaration.
		info.put("baseurl", getBaseUrl());

		// Add the title if appropriate.
		if (title != null && !"".equals(title)) {
			info.put("title", title);
		}

		// Add user name information
		info.put("username", getUsername());

		return info;
	}

	public String getUsername() {
		if (!username.isEmpty()) {
			return username;
		}

		ChallengeResponse cr = getRequest().getChallengeResponse();

		if (cr == null) {
			username = "UNKNOWN";
		} else {
			username = cr.getIdentifier();
		}

		return username;
	}

	protected String getBaseUrl() {
		return getRequest().getRootRef().toString();
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
		return PersistentDiskApplication.ZK_ROOT_PATH + "/" + uuid;
	}

	protected Boolean hasSuficientRightsToView(Properties properties) {
		// Is disk owner
		if (properties.get(DiskProperties.DISK_OWNER_KEY).toString()
				.equals(getUsername())) {
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

	protected Boolean hasSuficientRightsToDelete(Properties properties) {
		// Need to be the owner to delete the disk
		return properties.get(DiskProperties.DISK_OWNER_KEY).toString()
				.equals(getUsername());
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

	protected String getDateTime() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();

		return dateFormat.format(date);
	}

	public static DiskProperties getZooKeeper() {
		return zk;
	}

}
