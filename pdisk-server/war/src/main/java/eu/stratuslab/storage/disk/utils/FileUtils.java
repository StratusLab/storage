package eu.stratuslab.storage.disk.utils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;

public final class FileUtils {

    private static final String[] ALGORITHMS = { "MD5", "SHA-1", "SHA-256",
            "SHA-512" };

    private static final Logger LOGGER = Logger.getLogger("org.restlet");

    private static final int BUFFER_SIZE = 1024 * 50; // 50 MB buffer

    private FileUtils() {

    }

    public static Boolean isExecutable(String filename) {
        File exec = new File(filename);
        return isExecutable(exec);
    }

    public static Boolean isExecutable(File exec) {
        return exec.isFile() && exec.canExecute();
    }

    public static void appendToFile(File file, String contents) {
        Writer writer = null;

        try {
            writer = getDefaultCharsetWriter(file, true);
            writer.append(contents);
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while writting " + file.getName());
        } finally {
            closeRaisingError(writer, file.getName());
        }
    }

    public static void writeToFile(File file, String contents) {
        Writer writer = null;

        try {
            writer = getDefaultCharsetWriter(file);
            writer.write(contents);
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while writting "
                            + file.getAbsolutePath()
                            + ".\n Free space on device: "
                            + String.valueOf(file.getParentFile()
                                    .getFreeSpace() / (1024 * 1024)) + "MB.");
        } finally {
            closeRaisingError(writer, file.getAbsolutePath());
        }
    }

    public static void createZeroFile(File file, int sizeInGB) {
        OutputStream ostream = null;

        try {
            ostream = new FileOutputStream(file);

            // Create 1 MB buffer of zeros.
            byte[] buffer = new byte[1024000];

            for (int i = 0; i < 1000 * sizeInGB; i++) {
                ostream.write(buffer);
            }
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while creating a block file "
                            + file.getName());
        } finally {
            closeRaisingError(ostream, file.getName());
        }

    }

    public static Boolean fileHasLine(File file, String line) {
        Boolean isPresent = false;
        BufferedReader br = null;

        try {
            br = getDefaultCharsetReader(file);

            String currentLine;

            while ((currentLine = br.readLine()) != null) {
                if (currentLine.equals(line)) {
                    isPresent = true;
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured will reading file " + file.getName());
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured will reading file " + file.getName());
        } finally {
            closeRaisingError(br, file.getName());
        }

        return isPresent;
    }

    private static Writer getDefaultCharsetWriter(File file)
            throws FileNotFoundException {
        return getDefaultCharsetWriter(file, false);
    }

    private static Writer getDefaultCharsetWriter(File file, boolean append)
            throws FileNotFoundException {
        return new OutputStreamWriter(new FileOutputStream(file, append),
                Charset.defaultCharset());
    }

    private static BufferedReader getDefaultCharsetReader(File file)
            throws FileNotFoundException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(
                file), Charset.defaultCharset()));
    }

    public static void createZeroFile(File file, long sizeInGB) {
        OutputStream ostream = null;

        try {
            ostream = new FileOutputStream(file);

            // Create 1 MB buffer of zeros.
            byte[] buffer = new byte[1024000];

            for (int i = 0; i < 1000 * sizeInGB; i++) {
                ostream.write(buffer);
            }
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while creating a block file "
                            + file.getName());
        } finally {
            closeRaisingError(ostream, file.getName());
        }

    }

    public static void copyFile(String src, String dst) {
        ProcessBuilder pb = new ProcessBuilder("dd", "if=" + src, "of=" + dst);
        ProcessUtils.execute(pb, "Unable to copy file " + src + " to " + dst);
    }

    public static Map<String, BigInteger> copyStream(InputStream is,
            OutputStream os) throws IOException {

        BigInteger bytes = BigInteger.ZERO;

        Map<String, BigInteger> results = new HashMap<String, BigInteger>();
        results.put("BYTES", bytes);

        ArrayList<MessageDigest> mds = new ArrayList<MessageDigest>();
        for (String algorithm : ALGORITHMS) {
            try {
                mds.add(MessageDigest.getInstance(algorithm));
            } catch (NoSuchAlgorithmException consumed) {
                // Do nothing.
            }
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        for (int length = is.read(buffer); length > 0; length = is.read(buffer)) {

            bytes = bytes.add(BigInteger.valueOf(length));
            for (MessageDigest md : mds) {
                md.update(buffer, 0, length);
                os.write(buffer, 0, length);
            }
        }

        results.put("BYTES", bytes);

        for (MessageDigest md : mds) {
            results.put(md.getAlgorithm(), new BigInteger(1, md.digest()));
        }

        return results;
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
