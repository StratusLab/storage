package eu.stratuslab.storage.disk.resources;

import org.restlet.Request;
import org.restlet.resource.Get;

public class LogoutResource extends BaseResource {
	
	@Get
	public void logoutUser() {
		Request request = getRequest();
		
		// TODO Fix logout
		if (request.getClientInfo() == null) {
			request.getClientInfo().setAuthenticated(false);
		}
		
		redirectSeeOther(getBaseUrl());
	}
}
