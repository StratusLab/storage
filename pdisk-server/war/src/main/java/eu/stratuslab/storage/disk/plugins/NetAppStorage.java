package eu.stratuslab.storage.disk.plugins;

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

		ProcessUtils.execute(pb, "Unable to create volume on NetApp: " + uuid
				+ " of size " + size);
	}

	protected void checkDiskExists(String baseUuid) {

		ProcessBuilder pb = new ProcessBuilder(NETAPP_CMD, "--config",
				NETAPP_CONFIG, "--action", "check", baseUuid);

		ProcessUtils
				.execute(pb, "Volume does not exist on NetApp: " + baseUuid);

	}

	public String rebase(String cowUuid, String rebaseUuid) {

		ProcessBuilder pb = new ProcessBuilder(NETAPP_CMD, "--config",
				NETAPP_CONFIG, "--action", "rebase", cowUuid, rebaseUuid);

		ProcessUtils.execute(pb, "Cannot rebase image NetApp: " + cowUuid + " "
				+ rebaseUuid);

		return rebaseUuid;
	}

	public void createCopyOnWrite(String baseUuid, String cowUuid, int size) {

		ProcessBuilder pb = new ProcessBuilder(NETAPP_CMD, "--config",
				NETAPP_CONFIG, "--action", "snapshot", baseUuid, cowUuid,
				Integer.toString(size));

		ProcessUtils.execute(pb, "Cannot create copy on write volume: "
				+ baseUuid + " " + cowUuid + " " + size);

	}

	public void delete(String uuid) {
		ProcessBuilder pb = new ProcessBuilder(NETAPP_CMD, "--config",
				NETAPP_CONFIG, "--action", "delete", uuid, Integer.toString(0));

		ProcessUtils.execute(pb, "Unable to delete volume on NetApp: " + uuid);
	}
}
