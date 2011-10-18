package eu.stratuslab.storage.disk.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
        StringBuffer outputBuf = new StringBuffer();

        pb.redirectErrorStream(true);

        try {
            process = pb.start();

            BufferedReader stdOutErr = new BufferedReader(new InputStreamReader(
            		process.getInputStream()));
    		String line;
    		while ((line = stdOutErr.readLine()) != null) {
    			outputBuf.append(line);
    			outputBuf.append("\n");
    		}

            processWait(process);

            stdOutErr.close();

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

        	process.getErrorStream();

            String msg = "An error occurred while executing command: "
                    + MiscUtils.join(pb.command(), " ") + ".\n"
                    + outputBuf.toString() + "\n"
                    + errorMsg
                    + ".\nReturn code was: " + String.valueOf(returnCode);

            LOGGER.severe(msg);

            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, msg);
        }
    }


    public static int executeGetStatus(ProcessBuilder pb) {
    	Process process;
    	try {
	    	process = pb.start();
	        return processWaitGetStatus(process);
    	} catch (IOException e) {
    		return -1;
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

    private static int processWaitGetStatus(Process process) {
    	int rc = -1;
    	boolean blocked = true;
    	while (blocked) {
    		try {
    			rc = process.waitFor();
    			blocked = false;
    		} catch (InterruptedException consumed) {
    			// just continue to wait
    		}
    	}
    	return rc;
    }

}

