package eu.stratuslab.storage.disk.plugins;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.utils.ProcessUtils;

public final class NetAppStorage implements DiskStorage {

	private static final String NETAPP_CMD_DIR = "/Users/loomis/netapp/";
	private static final String NETAPP_CONFIG = NETAPP_CMD_DIR
			+ "pdisk-management.conf";
	private static final String NETAPP_CMD = NETAPP_CMD_DIR
			+ "persistent-disk-management.py";

	public void create(String uuid, int size) {

		ProcessBuilder pb = new ProcessBuilder(NETAPP_CMD, "--config",
				NETAPP_CONFIG, "--action", "create", uuid,
				Integer.toString(size));

		ProcessUtils.execute(pb, "Unable to create volume on NetApp");
	}

	protected void checkDiskExists(String baseUuid) {
		throw new ResourceException(Status.SERVER_ERROR_NOT_IMPLEMENTED,
				"NetApp exists function is not implemented");
	}

	public String rebase(String cowUuid, String rebaseUuid) {
		throw new ResourceException(Status.SERVER_ERROR_NOT_IMPLEMENTED,
				"NetApp rebase function is not implemented");
	}

	public void createCopyOnWrite(String baseUuid, String cowUuid, int size) {
		throw new ResourceException(Status.SERVER_ERROR_NOT_IMPLEMENTED,
				"NetApp copy on write function is not implemented");
	}

	public void delete(String uuid) {
		ProcessBuilder pb = new ProcessBuilder(NETAPP_CMD, "--config",
				NETAPP_CONFIG, "--action", "delete", uuid, Integer.toString(0));

		ProcessUtils.execute(pb, "Unable to delete volume on NetApp");
	}
}
