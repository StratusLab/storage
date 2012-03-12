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

import java.util.Map;
import java.util.Properties;

import org.restlet.data.Form;
import org.restlet.engine.util.Base64;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

public class LoginResource extends BaseResource {

	@Get("html")
	public Representation getLoginPage() {
		Map<String, Object> info = createInfoStructure("login");
		info.put("pageurl", getCurrentUrlWithQueryString());
		return createTemplateRepresentation("html/login.ftl", info, TEXT_HTML);
	}

	@Post("form:html")
	public Representation logUserIn(Representation entity) {
		getLogger().info("Login form submited");
		Properties loginProperties = getLoginProperties(new Form(entity));

		if (mandatoryAuthInfosNotProvided(loginProperties)) {
			getLogger().info("Some auth infos are missing");
			Map<String, Object> info = createInfoStructure("login error");
			info.put("pageurl", getCurrentUrlWithQueryString());
			info.put("error", "Please enter a correct username and password. Note that both fields are case-sensitive.");
			return createTemplateRepresentation("html/login.ftl", info,	TEXT_HTML);
		} 
	
		addCredentialHeader(loginProperties);					
		redirectSeeOther(getBaseUrl());

		return null;
	}

	private boolean mandatoryAuthInfosNotProvided(Properties loginProperties) {
		return loginProperties.getProperty("username", "").isEmpty()
				|| loginProperties.getProperty("password", "").isEmpty();
	}
	
	private Properties getLoginProperties(Form form) {
		Properties loginProperties = processWebForm(new Properties(), form);
		return loginProperties;
	}
	
	private void addCredentialHeader(Properties loginProperties) {
        Form loginHeaders = (Form) getResponse().getAttributes().get(
                "org.restlet.http.headers");
        String username = loginProperties.get("username").toString();
        String password = loginProperties.get("password").toString();
        String authorization = "Basic " + Base64.encode((username + ":" + password).toCharArray(), false);
        
		getLogger().info("Auth: " + username + " " + password);
		getLogger().info("Authorization " + authorization);

		if (loginHeaders == null) {
            loginHeaders = new Form();
            getResponse().getAttributes().put("org.restlet.http.headers",
                    loginHeaders);
        }

        loginHeaders.add("Authorization", authorization);
    }

}
