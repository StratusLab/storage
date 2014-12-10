package eu.stratuslab.storage.disk.utils;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;
import org.junit.Ignore;

public class DownloadUtilsTest {

	@Test
	public void testGetHttpClientWithProxy() throws MalformedURLException {

		DefaultHttpClient client = DownloadUtils
				.getHttpClientWithProxy("http://www.google.com");

		HttpHost proxy = (HttpHost) client.getParams().getParameter(
				ConnRoutePNames.DEFAULT_PROXY);
		if (DownloadUtils.isEnvProxySet()) {
			assertNotNull(proxy);
			URL url = new URL(DownloadUtils.getEnvProxy());
			assertThat(proxy.getHostName(), is(url.getHost()));
			assertThat(proxy.getPort(), is(url.getPort()));
		} else {
			assertNull(proxy);
		}
	}

	@Ignore
	@Test
	public void testCopyUrlContentsToFile() throws IOException {
		String url = "http://ipv4.download.thinkbroadband.com/5MB.zip";
		String sha1 = "0cc897be1958c0f44371a8ff3dddbc092ff530d0";
		int maxFiles = 1;
		int bufferSize = 16 * 1024 * 1024;

		List<File> files = new ArrayList<File>();
		for (int i = 1; i <= maxFiles; i++) {
			files.add(new File(System.getProperty("java.io.tmpdir") + "/5MB.zip." + i));
		}
		Map<String, BigInteger> streamInfo = new HashMap<String, BigInteger>();
	    streamInfo = DownloadUtils.copyUrlContentsToFiles(url, files, bufferSize);
		org.junit.Assert.assertEquals(new BigInteger(sha1, 16), streamInfo.get("SHA-1"));
		for (File file : files) {
			try {
				file.delete();
			} catch (Exception ex) {
				//
			}
		}
	}
}
