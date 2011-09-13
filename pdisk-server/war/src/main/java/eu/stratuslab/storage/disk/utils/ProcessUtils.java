package eu.stratuslab.storage.disk.utils;

import java.io.IOException;
import java.util.logging.Logger;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public final class ProcessUtils {

    private static final Logger LOGGER = Logger.getLogger("org.restlet");

    private ProcessUtils() {

    }

    public static void execute(ProcessBuilder pb, String errorMsg) {
        int returnCode = 1;
        Process process;

        try {
            process = pb.start();
            processWait(process);

            returnCode = process.exitValue();
        } catch (IOException e) {

            String msg = "An error occurred while executing command: "
                    + MiscUtils.join(pb.command(), " ") + ".\n" + errorMsg
                    + ".";

            LOGGER.severe(msg);
            LOGGER.severe(e.getMessage());

            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, msg);
        }

        if (returnCode != 0) {

            String msg = "An error occurred while executing command: "
                    + MiscUtils.join(pb.command(), " ") + ".\n" + errorMsg
                    + ".\nReturn code was: " + String.valueOf(returnCode);

            LOGGER.severe(msg);

            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, msg);
        }
    }

    private static void processWait(Process process) {
        boolean blocked = true;
        while (blocked) {
            try {
                process.waitFor();
                blocked = false;
            } catch (InterruptedException consumed) {
                // just continue to wait
            }
        }

    }

}
