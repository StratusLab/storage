package eu.stratuslab.storage.disk.utils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;

public final class FileUtils {

    private static final Logger LOGGER = Logger.getLogger("org.restlet");

    private FileUtils() {

    }

    public static Boolean isExecutable(File exec) {
        return exec.isFile() && exec.canExecute();
    }

    public static void copyFile(String src, String dst) {
        ProcessBuilder pb = new ProcessBuilder("dd", "if=" + src, "of=" + dst);
        ProcessUtils.execute(pb, "Unable to copy file " + src + " to " + dst);
    }

    public static File getUploadCacheDirectory() {
        return new File(RootApplication.CONFIGURATION.CACHE_LOCATION);
    }

    public static File getCachedDiskFile(String uuid) {
        return new File(getUploadCacheDirectory(), uuid);
    }

    public static String getCompressedDiskLocation(String uuid) {
        return getCachedDiskFile(uuid).getAbsolutePath() + ".gz";
    }

    public static Boolean isCachedDiskExists(String uuid) {
        return getCachedDiskFile(uuid).canRead(); // implies exists()
    }

    public static Boolean isCompressedDiskExists(String uuid) {
        File cachedDisk = new File(getCompressedDiskLocation(uuid));
        return cachedDisk.canRead(); // implies exists()
    }

    public static void closeIgnoringError(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException consumed) {
            // ignored
        }
    }

    public static void closeRaisingError(Closeable c, String filename) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException e) {
            LOGGER.warning("an error occurred while closing file: " + filename
                    + "\n" + e.getMessage());
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occurred while closing a file.");
        }
    }

}
