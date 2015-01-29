package eu.stratuslab.storage.disk.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.marketplace.metadata.MetadataUtils;
import eu.stratuslab.storage.disk.backend.BackEndStorage;
import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.Disk.DiskType;

/**
 * For unit tests see {@link DiskUtilsTest}
 *
 */
public final class DiskUtils {

	private static final Logger LOGGER = Logger.getLogger("org.restlet");

	// This defines one GibiByte (GiB, 2^30 bytes). GigaBytes (GB) is used
	// as a synonym for GiB throughout the code and in the API.
	public static final long BYTES_IN_GiB = 1024L * 1024L * 1024L;
	public static final double BYTES_IN_GiB_DOUBLE = (double) BYTES_IN_GiB;

	private DiskUtils() {

	}

	public static BackEndStorage getDiskStorage() {

		return new BackEndStorage();

	}

	public static void map(String uuid, String proxy) {
		BackEndStorage backend = getDiskStorage();
		backend.map(uuid, proxy);
	}

	public static String getTurl(String diskUuid) {
		BackEndStorage backend = getDiskStorage();
		return backend.getTurl(diskUuid);
	}

	public static String getTurl(String diskUuid, String proxy) {
		BackEndStorage backend = getDiskStorage();
		return backend.getTurl(diskUuid, proxy);
	}

	/**
	 * Create volume.  If the disk is MACHINE_IMAGE_ORIGIN the volume is
	 * created on all the backends.  Otherwise, the volume is created only
	 * on one randomly chosen backend.
	 * @param disk
	 */
	public static void createDisk(Disk disk) {
		if (DiskType.MACHINE_IMAGE_ORIGIN == disk.getType()) {
			createDiskOnAllBackends(disk);
		} else {
			createDisk(disk, getRandomBackendProxyFromConfig());
		}
	}

	public static void createDisk(Disk disk, String proxy) {
		BackEndStorage diskStorage = getDiskStorage();
		diskStorage.create(disk.getUuid(), disk.getSize(), proxy);
		diskStorage.map(disk.getUuid(), proxy);
		LOGGER.info("createDisk: Adding backend proxy " + proxy + " to " + disk.getBackendProxies());
		disk.addBackendProxy(proxy);
	}

	public static void createDiskOnAllBackends(Disk disk) {
		for (String proxy : getBackendProxiesFromConfig()) {
			createDisk(disk, proxy);
		}
	}

	public static String[] getBackendProxiesFromConfig() {
		return ServiceConfiguration.getInstance().BACKEND_PROXIES;
	}

	public static String getRandomBackendProxyFromConfig() {
		String[] proxies = getBackendProxiesFromConfig();
		int ind = new Random().nextInt(proxies.length);
		return proxies[ind];
	}

	public static String getFirstBackendProxyFromConfig() {
		return getBackendProxiesFromConfig()[0];
	}

	public static Disk createMachineImageCoWDisk(Disk disk) {

		BackEndStorage diskStorage = getDiskStorage();

		Disk cowDisk = createCowDisk(disk);

		String proxy = disk.getRandomBackendProxy();
		// All old origins are on the first backend.
		if (proxy.isEmpty()) {
			proxy = getFirstBackendProxyFromConfig();
		}
		diskStorage.createCopyOnWrite(disk.getUuid(), cowDisk.getUuid(),
				disk.getSize(), proxy);

		cowDisk.setType(DiskType.MACHINE_IMAGE_LIVE);
		diskStorage.map(cowDisk.getUuid(), proxy);
		// MACHINE_IMAGE_LIVE can only be on one backed.
		cowDisk.setBackendProxies(proxy);

		cowDisk.store();

		return cowDisk;
	}

	protected static Disk createCowDisk(Disk disk) {
		Disk cowDisk = new Disk();
		cowDisk.setType(DiskType.DATA_IMAGE_LIVE);
		cowDisk.setBaseDiskUuid(disk.getUuid());
		cowDisk.setSize(disk.getSize());
		cowDisk.setIdentifier("snapshot:" + disk.getUuid());
		return cowDisk;
	}

	/**
	 * Rebase of the live disk.  Only one backend is assumed to be set on the
	 * disk, or none.  The latter happens with the running VMs during the transitional
	 * period.  In this case we take the first backend proxy from the configuration file.
	 * @param disk
	 * @return
	 */
	public static String rebaseDisk(Disk disk) {

		String uuid = disk.getUuid();

		BackEndStorage diskStorage = getDiskStorage();
		String[] proxies = disk.getBackendProxiesArray();

		if (DiskType.MACHINE_IMAGE_LIVE == disk.getType()) {
    		if (proxies.length == 1) {
    			return diskStorage.rebase(uuid, proxies[0]);
    		} else if (proxies.length == 0) {
    			// Get first proxy from configuration. This is the case with the
    			// running VMs during the transitional period.
    			return diskStorage.rebase(uuid, getFirstBackendProxyFromConfig());
    		}
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Failed rebasing disk: " + uuid
			        + ". Expected less than two backends, but got: " + disk.getBackendProxies());
		}
		throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Failed rebasing disk: " + uuid
		        + ". Only live machine image can be rebased. Given: " + disk.getType().toString());
	}

	/**
	 * Copy the volume from the current backend(s) to all the others found
	 * in the configuration.
	 * @param disk
	 */
	public static void distributeAmongAllBackends(Disk disk) {

		BackEndStorage diskStorage = getDiskStorage();

		String[] currentProxies = disk.getBackendProxiesArray();
		String baseProxy = currentProxies[0];
		String uuid = disk.getUuid();

		diskStorage.map(uuid, baseProxy);

		String linkBaseDisk = attachDiskToThisHost(uuid, baseProxy);

		List<String> proxies = new ArrayList<String>(Arrays.asList(getBackendProxiesFromConfig()));
		proxies.removeAll(Arrays.asList(currentProxies));
		for (String proxy : proxies) {
			createDisk(disk, proxy);

			String linkNewDisk = attachDiskToThisHost(uuid, proxy);

			try {
				try {
					FileUtils.copyFile(linkBaseDisk, linkNewDisk);
				} finally {
					detachDiskFromThisHost(uuid, proxy);
					getDiskStorage().unmap(uuid, proxy);
					File linkNewDiskFile = new File(linkNewDisk);
					if (!linkNewDiskFile.delete()) {
						LOGGER.warning("Failed to delete: " + linkNewDisk);
					}
				}
			} catch (ResourceException e) {
				removeDisk(disk);
			}

			disk.addBackendProxy(proxy);

		}

		disk.store();

		detachDiskFromThisHost(uuid, baseProxy);
		diskStorage.unmap(uuid, baseProxy);
	}

	public static void removeDisk(Disk disk) {
    	String uuid = disk.getUuid();
    	LOGGER.info("removeDisk: removing " + uuid + " from backends " + disk.getBackendProxies());
    	for (String proxy : disk.getBackendProxiesArray()) {
    		getDiskStorage().unmap(uuid, proxy);
    		getDiskStorage().delete(uuid, proxy);
    		disk.removeBackendProxy(proxy);
    	}
	}

	public static String getDiskId(String host, int port, String uuid) {
		return String.format("pdisk:%s:%d:%s", host, port, uuid);
	}

	public static void attachHotplugDisk(String serviceName, int servicePort,
			String node, String vmId, String diskUuid, String target,
			String turl) {

		// Do NOT use the --register flag here. This may cause an infinite loop
		// in the process because it calls the pdisk service again.

		List<String> cmd = createHotPlugCommand(node);
		cmd.add("--op up");

		cmd.add("--attach");
		cmd.add("--mark");
		cmd.add("--link");
		cmd.add("--mount");

		cmd.add("--pdisk-id");
		cmd.add(getDiskId(serviceName, servicePort, diskUuid));

		cmd.add("--target");
		cmd.add(target);

		cmd.add("--vm-id");
		cmd.add(vmId);

		cmd.add("--turl");
		cmd.add(turl);

		cmd.add("--vm-disk-name");
		cmd.add(getDiskId(serviceName, servicePort, diskUuid));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		ProcessUtils.execute(pb, "Unable to attach persistent disk");
	}

	public static String attachHotplugDisk(String diskUuid) {
		int port = ServiceConfiguration.getInstance().PDISK_SERVER_PORT;
		String host = "localhost";
		String tmpVmId = DiskUtils.generateUUID();

		String turl = getTurl(diskUuid);

		// FIXME: host is most probably wrong for the last parameter
		attachHotplugDisk(host, port, host, tmpVmId, diskUuid, host, turl);

		return tmpVmId;
	}

	protected static String getDiskLocation(String vmId, String diskUuid) {
		String attachedDisk = RootApplication.CONFIGURATION.CLOUD_NODE_VM_DIR
				+ "/" + vmId + "/images/pdisk-" + diskUuid;
		return attachedDisk;
	}

	public static void detachHotplugDisk(String serviceName, int servicePort,
			String node, String vmId, String diskUuid, String target,
			String turl) {

		// Do NOT use the --register flag here. This may cause an infinite loop
		// in the process because it calls the pdisk service again.

		List<String> cmd = createHotPlugCommand(node);
		cmd.add("--op down");

		cmd.add("--attach");
		cmd.add("--mark");
		cmd.add("--link");
		cmd.add("--mount");

		cmd.add("--pdisk-id");
		cmd.add(getDiskId(serviceName, servicePort, diskUuid));

		cmd.add("--target");
		cmd.add(target);

		cmd.add("--vm-id");
		cmd.add(vmId);

		cmd.add("--turl");
		cmd.add(turl);

		cmd.add("--vm-disk-name");
		cmd.add(getDiskId(serviceName, servicePort, diskUuid));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		ProcessUtils.execute(pb, "Unable to detach persistent disk");
	}

	protected static List<String> createHotPlugCommand(String node) {
		List<String> cmd = new ArrayList<String>();
		cmd.add("ssh");
		cmd.add("-p");
		cmd.add("22");
		cmd.add("-o");
		cmd.add("ConnectTimeout=5");
		cmd.add("-o");
		cmd.add("StrictHostKeyChecking=no");
		cmd.add("-i");
		cmd.add(RootApplication.CONFIGURATION.CLOUD_NODE_SSH_KEY);
		cmd.add(RootApplication.CONFIGURATION.CLOUD_NODE_ADMIN + "@" + node);
		cmd.add("/usr/sbin/stratus-pdisk-client.py");
		return cmd;
	}

	public static String generateUUID() {
		return UUID.randomUUID().toString();
	}

	public static boolean isValidUUID(String uuid) {
		try {
			UUID.fromString(uuid);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static String calculateHash(String uuid)
			throws FileNotFoundException {

		InputStream fis = null;// = new FileInputStream(getDevicePath() + uuid);

		return calculateHash(fis);

	}

	public static String calculateHash(File file) throws FileNotFoundException {

		InputStream fis = new FileInputStream(file);

		return calculateHash(fis);

	}

	public static String calculateHash(InputStream fis)
			throws FileNotFoundException {

		Map<String, BigInteger> info = MetadataUtils.streamInfo(fis);

		BigInteger sha1Digest = info.get("SHA-1");

		String identifier = MetadataUtils.sha1ToIdentifier(sha1Digest);

		return identifier;

	}

	public static String getDevicePath() {
		return "";// RootApplication.CONFIGURATION.LVM_GROUP_PATH + "/";
	}

	/**
	 * DATA_IMAGE_RAW_READONLY volume gets created.
	 * @param disk
	 */
	public static void createAndPopulateDiskLocal(Disk disk) {

		String uuid = disk.getUuid();

		File cachedDiskFile = FileUtils.getCachedDiskFile(uuid);
		String cachedDisk = cachedDiskFile.getAbsolutePath();

		try {

			disk.setType(DiskType.DATA_IMAGE_RAW_READONLY);

			createDisk(disk);

			String proxy = disk.getBackendProxies();

			try {
				copyContentsToVolume(uuid, proxy, cachedDisk);

				// Size has already been set on the disk.
				disk.setSeed(true);

			} catch (RuntimeException e) {
				removeDisk(disk);
			}

		} finally {
			if (!cachedDiskFile.delete()) {
				LOGGER.warning("could not delete upload cache file: "
						+ cachedDisk);
			}
		}
	}

	private static void copyContentsToVolume(String uuid, String proxy, String cachedDisk) {
		String diskLocation = attachDiskToThisHost(uuid, proxy);
		try {
			FileUtils.copyFile(cachedDisk, diskLocation);
		} finally {
			detachDiskFromThisHost(uuid, proxy);
			getDiskStorage().unmap(uuid, proxy);
		}
	}

	public static Map<String, BigInteger> copyUrlToVolume(String uuid, String proxy,
			String url) throws IOException {

		File diskLocation = new File(attachDiskToThisHost(uuid, proxy));
		try {
			return DownloadUtils.copyUrlContentsToFile(url, diskLocation);
		} finally {
			detachDiskFromThisHost(uuid, proxy);
			getDiskStorage().unmap(uuid, proxy);
		}
	}

	public static Map<String, BigInteger> copyUrlToVolumes(String uuid, String[] proxies,
			String url) throws IOException {

		List<File> diskLocations = new ArrayList<File>();
		for (String proxy : proxies) {
			diskLocations.add(new File(attachDiskToThisHost(uuid, proxy)));
		}
		try {
			int bufferSize = getUrlDownloadBufferSize();
			return DownloadUtils.copyUrlContentsToFiles(url, diskLocations, bufferSize);
		} finally {
			for (String proxy : proxies) {
    			detachDiskFromThisHost(uuid, proxy);
    			getDiskStorage().unmap(uuid, proxy);
			}
		}
	}

	private static int getUrlDownloadBufferSize() {
		// Zero forces stream downloader to use its own default.
		try {
			return ServiceConfiguration.getInstance().DOWNLOAD_STREAM_BUFFER_SIZE;
		} catch (Exception e){
			return 0;
		}
    }

	/**
	 * Returns the minimum number of whole GibiBytes (2^30 bytes) that contains
	 * at least the number of bytes given as the argument. If the argument is
	 * not positive, then the return value is 1L.
	 *
	 * @param sizeInBytes
	 */
	public static long convertBytesToGibiBytes(long sizeInBytes) {
		long inGiB = (long) Math.ceil(sizeInBytes / BYTES_IN_GiB_DOUBLE);
		return (inGiB <= 0 ? 1L : inGiB);
	}

	public static void createCompressedDisk(String uuid, String proxy) {

		String diskLocation = attachDiskToThisHost(uuid, proxy);

		ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c",
				RootApplication.CONFIGURATION.GZIP_CMD + " -f -c "
						+ diskLocation + " > "
						+ getCompressedDiskLocation(uuid));
		ProcessUtils.execute(pb, "Unable to compress disk " + uuid);

		detachDiskFromThisHost(uuid, proxy);
	}

	private static String attachDiskToThisHost(String uuid, String proxy) {

		String host = "localhost";
		int port = ServiceConfiguration.getInstance().PDISK_SERVER_PORT;

		String linkName = getLinkedVolumeInDownloadCache(uuid, proxy);

		String turl = getTurl(uuid, proxy);

		List<String> cmd = getCommandAttachAndLinkLocal(uuid, host, port,
				linkName, turl);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		ProcessUtils.execute(pb, "Unable to attach persistent disk " + uuid +
				" on backend " + proxy);

		return linkName;
	}

	private static void detachDiskFromThisHost(String uuid, String proxy) {
		unlinkVolumeFromDownloadCache(uuid, proxy);

		String host = "localhost";
		int port = ServiceConfiguration.getInstance().PDISK_SERVER_PORT;

		BackEndStorage backend = getDiskStorage();
		String turl = backend.getTurl(uuid, proxy);

		List<String> cmd = getCommandDetachLocal(uuid, host, port, turl);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		ProcessUtils.execute(pb, "Unable to detach persistent disk " + uuid);
	}

	private static void unlinkVolumeFromDownloadCache(String uuid, String proxy) {
		String linkName = getLinkedVolumeInDownloadCache(uuid, proxy);
		File file = new File(linkName);
		if (!file.delete()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Failed deleting linked file: " + linkName);
		}
	}

	private static List<String> getCommandAttachAndLinkLocal(String uuid,
			String host, int port, String linkName, String turl) {
		List<String> cmd = new ArrayList<String>();

		cmd.add("/usr/sbin/stratus-pdisk-client.py");

		cmd.add("--op");
		cmd.add("up");

		cmd.add("--attach");

		cmd.add("--pdisk-id");
		cmd.add(getDiskId(host, port, uuid));

		cmd.add("--link-to");
		cmd.add(linkName);

		cmd.add("--turl");
		cmd.add(turl);

		return cmd;
	}

	private static List<String> getCommandDetachLocal(String uuid, String host,
			int port, String turl) {
		List<String> cmd = new ArrayList<String>();

		cmd.add("/usr/sbin/stratus-pdisk-client.py");

		cmd.add("--op");
		cmd.add("down");

		cmd.add("--attach");

		cmd.add("--pdisk-id");
		cmd.add(getDiskId(host, port, uuid));

		cmd.add("--turl");
		cmd.add(turl);

		return cmd;
	}

	private static String getLinkedVolumeInDownloadCache(String uuid, String proxy) {
		return RootApplication.CONFIGURATION.CACHE_LOCATION + "/" + uuid
				+ "-" + proxy + ".link";
	}

	public static String getCompressedDiskLocation(String uuid) {
		return RootApplication.CONFIGURATION.CACHE_LOCATION + "/" + uuid
				+ ".gz";
	}

	public static Boolean isCompressedDiskBuilding(String uuid) {
		return FileUtils.isCachedDiskExists(uuid);
	}

	public static Boolean hasCompressedDiskExpire(String uuid) {
		File zip = new File(FileUtils.getCompressedDiskLocation(uuid));
		return hasCompressedDiskExpire(zip);
	}

	public static Boolean hasCompressedDiskExpire(File disk) {
		Calendar cal = Calendar.getInstance();
		return (cal.getTimeInMillis() > (disk.lastModified() + ServiceConfiguration.CACHE_EXPIRATION_DURATION));
	}

}
