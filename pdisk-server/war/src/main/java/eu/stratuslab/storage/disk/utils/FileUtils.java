package eu.stratuslab.storage.disk.utils;

import java.io.BufferedReader;
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

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public final class FileUtils {

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
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException consumed) {
                    throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                            "An error occured while closing " + file.getName());
                }
            }
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
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException consumed) {
                    throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                            "An error occured while closing "
                                    + file.getAbsolutePath());
                }
            }
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
            if (ostream != null) {
                try {
                    ostream.close();
                } catch (IOException consumed) {
                    throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                            "An error occured while closing block file "
                                    + file.getName());
                }
            }
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
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                        "An error occured while closing file " + file.getName());
            }
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
}
