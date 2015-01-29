package eu.stratuslab.storage.disk.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.utils.ProcessUtils;
import eu.stratuslab.storage.persistence.Disk;

public final class BackEndStorage {

	private String CONFIG = "/etc/stratuslab/pdisk-backend.cfg";
	private String CMD = "/usr/sbin/persistent-disk-backend.py";

    public void create(String uuid, long size, String proxy) {
		List<String> args = new ArrayList<String>();
		args.add(uuid);
		args.add(String.valueOf(size));
		prependIscsiProxyParamToArgs(args, proxy);

		String errorMsg = "Unable to create volume on backend storage: " + uuid
				+ " of size " + size + " on backend " + proxy;
		execute("create", errorMsg, args);
	}

	protected String execute(String action, String errorMsg,
			List<String> arguments) {
		String[] preArgs = { CMD, "--config", CONFIG, "--action", action };
		List<String> args = new ArrayList<String>();
		for (String s : preArgs) {
			args.add(s);
		}
		for (String s : arguments) {
			args.add(s);
		}

		return execute(errorMsg, args.toArray(new String[args.size()]));
	}

	private String execute(String errorMsg, String... arguments) {
		ProcessBuilder pb = new ProcessBuilder(arguments);

		return ProcessUtils.executeWithOutput(pb, errorMsg);
	}

	// TODO: expose this
	protected String checkDiskExists(String baseUuid) {
		List<String> args = new ArrayList<String>();
		args.add(baseUuid);

		condRandomPrependIscsiProxyParamToArgs(args, baseUuid);

		return execute("check", "Volume does not exist on backend storage: " + baseUuid,
				args);
	}

	public String getTurl(String baseUuid) {
		List<String> args = new ArrayList<String>();
		args.add(baseUuid);

		condRandomPrependIscsiProxyParamToArgs(args, baseUuid);

		return execute("getturl", "Cannot find transport URL (turl) for uuid: "
				+ baseUuid, args).trim();

	}

	public String getTurl(String baseUuid, String proxy) {
		List<String> args = new ArrayList<String>();
		args.add(baseUuid);
		prependIscsiProxyParamToArgs(args, proxy);

		return execute("getturl", "Cannot find TURL for uuid: "
				+ baseUuid + " on backend " + proxy, args).trim();

	}

	public String rebase(String uuid, String proxy) {
		List<String> args = new ArrayList<String>();
		args.add(uuid);
		prependIscsiProxyParamToArgs(args, proxy);

		String errorMsg = "Cannot rebase image: " + uuid + " on backend " + proxy;
		String rebasedUuid = execute("rebase", errorMsg, args);

		return rebasedUuid;
	}

	public String createCopyOnWrite(String baseUuid, String cowUuid, long size, String proxy) {

		List<String> args = new ArrayList<String>();
		args.add(baseUuid);
		args.add(cowUuid);
		args.add(Long.toString(size));
		prependIscsiProxyParamToArgs(args, proxy);

		String errorMsg = "Cannot create copy on write volume: " + baseUuid
				+ " " + cowUuid + " " + size + " on backend " + proxy;
		return execute("snapshot", errorMsg, args);
	}

	public void delete(String uuid, String proxy) {
		List<String> args = new ArrayList<String>();
		args.add(uuid);
		args.add("0");
		prependIscsiProxyParamToArgs(args, proxy);

		String errorMsg = "Unable to delete volume: " + uuid + " on backend " + proxy;

		execute("delete", errorMsg, args);
	}

	public String getDiskLocation(String vmId, String diskUuid) {
		String attachedDisk = RootApplication.CONFIGURATION.CLOUD_NODE_VM_DIR
				+ "/" + vmId + "/images/pdisk-" + diskUuid;
		return attachedDisk;
	}

	public void map(String uuid, String proxy) {
		List<String> args = new ArrayList<String>();
		args.add(uuid);
		prependIscsiProxyParamToArgs(args, proxy);

		execute("map", "Unable to map: " + uuid + " on backend " + proxy,
				args);
	}

	public void unmap(String uuid, String proxy) {
		List<String> args = new ArrayList<String>();
		args.add(uuid);
		prependIscsiProxyParamToArgs(args, proxy);

		execute("unmap", "Unable to unmap: " + uuid + " from " + proxy, args);
	}

	public Integer getNumberOfMappedLuns(String proxy) {
		List<String> args = new ArrayList<String>();
		prependIscsiProxyParamToArgs(args, proxy);

		String errorMsg = "Failed to get number of mapped LUNs in volume: " + proxy;
		return Integer.parseInt(execute("mappedluns", errorMsg, args));
	}

	private void condRandomPrependIscsiProxyParamToArgs(List<String> args, String uuid) {
		Disk disk = Disk.load(uuid);
		String[] proxies = disk.getBackendProxiesArray();
		if (proxies.length != 0) {
			int ind = new Random().nextInt(proxies.length);
			prependIscsiProxyParamToArgs(args, proxies[ind]);
		}
	}

	private void prependIscsiProxyParamToArgs(List<String> args, String proxy) {
		args.add(0, proxy);
		args.add(0, "--iscsi-proxy");
	}
}
