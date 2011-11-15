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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.restlet.Request;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.resource.ServerResource;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskProperties;
import freemarker.template.Configuration;

public class BaseResource extends ServerResource {

	protected final DiskProperties zk;

	public enum DiskVisibility {
		PRIVATE,
		// RESTRICTED,
		PUBLIC;

		public static DiskVisibility valueOfIgnoreCase(String value) {
			return valueOf(value.toUpperCase());
		}
	}

	public BaseResource() {
		super();
		zk = new DiskProperties();
	}

	@Override
	protected void doRelease() {
		if (zk != null) {
			zk.close();
		}
		super.doRelease();
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

	protected Map<String, Object> createInfoStructure(String title) {

		return createInfoStructure(title, getRequest(), getBaseUrl());
	}

	public static Map<String, Object> createInfoStructure(String title,
			Request request, String baseUrl) {

		Map<String, Object> info = new HashMap<String, Object>();

		// Add the title if appropriate.
		if (title != null && !"".equals(title)) {
			info.put("title", title);
		}

		// Add the standard base URL declaration.
		info.put("baseurl", baseUrl);

		// Add user name information
		info.put("username", getUsername(request));

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

	protected Boolean hasSufficientRightsToView(Properties properties) {
		String username = getUsername(getRequest());
		if (properties.get(DiskProperties.DISK_OWNER_KEY).toString()
				.equals(username)
				|| isSuperUser(username)) {
			return true;
		}

		String visibility = properties
				.getProperty(DiskProperties.DISK_VISIBILITY_KEY);

		DiskVisibility currentVisibility = DiskVisibility
				.valueOfIgnoreCase(visibility);

		return (currentVisibility == DiskVisibility.PUBLIC);
	}

	protected boolean isSuperUser(String username) {
		return RootApplication.CONFIGURATION.CLOUD_SERVICE_USER
				.equals(username);
	}

	protected Boolean hasSufficientRightsToDelete(Properties properties) {
		String username = getUsername(getRequest());
		return properties.get(DiskProperties.DISK_OWNER_KEY).toString()
				.equals(username)
				|| isSuperUser(username);
	}

	protected String serviceName() {
		return getRequest().getHostRef().getHostDomain();
	}

	protected int servicePort() {
		return getRequest().getHostRef().getHostPort();
	}

	public Configuration extractFmConfiguration() {
		Request request = getRequest();
		return extractFmConfiguration(request);
	}

	public static Configuration extractFmConfiguration(Request request) {
		try {
			Map<String, Object> attributes = request.getAttributes();
			Object value = attributes.get(RootApplication.FM_CONFIGURATION_KEY);
			return (Configuration) value;
		} catch (ClassCastException e) {
			return null;
		}
	}

	public ServiceConfiguration extractSvcConfiguration() {
		Request request = getRequest();
		return extractSvcConfiguration(request);
	}

	public static ServiceConfiguration extractSvcConfiguration(Request request) {
		try {
			Map<String, Object> attributes = request.getAttributes();
			Object value = attributes
					.get(RootApplication.SVC_CONFIGURATION_KEY);
			return (ServiceConfiguration) value;
		} catch (ClassCastException e) {
			return null;
		}
	}

}
