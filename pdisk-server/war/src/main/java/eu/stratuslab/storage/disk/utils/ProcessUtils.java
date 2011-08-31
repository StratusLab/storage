package eu.stratuslab.storage.disk.utils;

import java.io.IOException;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public class ProcessUtils {

    public static void execute(ProcessBuilder pb, String errorMsg) {
        int returnCode = 1;
        Process process;

        try {
            process = pb.start();

            boolean blocked = true;
            while (blocked) {
                try {
                    process.waitFor();
                    blocked = false;
                } catch (InterruptedException consumed) {
                    // just continue to wait
                }
            }

            returnCode = process.exitValue();
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occurred while executing command: "
                            + MiscUtils.join(pb.command(), " ") + ".\n"
                            + errorMsg + ".");
        }

        if (returnCode != 0) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occurred while executing command: "
                            + MiscUtils.join(pb.command(), " ") + ".\n"
                            + errorMsg + ".\nReturn code was: "
                            + String.valueOf(returnCode));
        }
    }

}
