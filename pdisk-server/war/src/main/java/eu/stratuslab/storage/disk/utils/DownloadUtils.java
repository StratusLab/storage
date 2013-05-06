package eu.stratuslab.storage.disk.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

public class DownloadUtils {

    public static void copyUrlContentsToFile(String url, File file)
            throws IOException {

        DefaultHttpClient client = new DefaultHttpClient();
        client.addRequestInterceptor(new GzipRequestInterceptor());
        client.addResponseInterceptor(new GzipResponseInterceptor());

        try {

            HttpGet get = new HttpGet(url);

            HttpResponse response = client.execute(get);

            HttpEntity entity = response.getEntity();

            if (entity != null) {

                InputStream is = null;
                OutputStream os = null;

                try {
                    is = entity.getContent();
                    os = new FileOutputStream(file);
                    FileUtils.copyStream(is, os);
                } finally {
                    FileUtils.closeIgnoringError(is);
                    FileUtils.closeIgnoringError(os);
                }
            }

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private static class GzipRequestInterceptor implements
            HttpRequestInterceptor {

        public void process(final HttpRequest request, final HttpContext context)
                throws HttpException, IOException {

            if (!request.containsHeader("Accept-Encoding")) {
                request.addHeader("Accept-Encoding", "gzip");
            }
        }
    }

    private static class GzipResponseInterceptor implements
            HttpResponseInterceptor {

        public void process(final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(new GzipDecompressingEntity(
                                    response.getEntity()));
                            return;
                        }
                    }
                }
            }
        }

    }

}
