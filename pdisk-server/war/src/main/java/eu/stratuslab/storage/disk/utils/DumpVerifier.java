package eu.stratuslab.storage.disk.utils;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.security.Verifier;

public class DumpVerifier implements Verifier {

	public int verify(Request request, Response response) {
		// As JAAS has already done all the authentication job, we just have to
		// accept the client "as is" without further check
		return RESULT_VALID;
	}
    
}
