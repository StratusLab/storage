package eu.stratuslab.storage.disk.utils;

import static org.junit.Assert.assertNull;

import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;

public class DownloadUtilsTest {

    @Test
    public void testGetHttpClientWithProxy() {

    	DefaultHttpClient client = DownloadUtils.getHttpClientWithProxy("http://www.google.com");

    	HttpHost proxy = (HttpHost) client.getParams().getParameter(ConnRoutePNames.DEFAULT_PROXY);
    	assertNull(proxy);
    }

}
