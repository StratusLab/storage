package eu.stratuslab.storage.disk.utils;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;

public class DownloadUtilsTest {

	@Test
	public void testGetHttpClientWithProxy() throws MalformedURLException {

		DefaultHttpClient client = DownloadUtils
				.getHttpClientWithProxy("http://www.google.com");

		HttpHost proxy = (HttpHost) client.getParams().getParameter(
				ConnRoutePNames.DEFAULT_PROXY);
		if (DownloadUtils.isEnvProxySet()) {
			assertNotNull(proxy);
			String proxyUrl = DownloadUtils.getEnvProxy();
			URL url = new URL(proxyUrl);
			assertThat(proxy.getHostName(), is(url.getHost()));
			assertThat(proxy.getPort(), is(url.getPort()));
		} else {
			assertNull(proxy);
		}
	}

}
