package eu.stratuslab.storage.disk.utils;

import eu.stratuslab.marketplace.metadata.MetadataUtils;
import eu.stratuslab.storage.disk.backend.BackEndStorage;
import eu.stratuslab.storage.disk.main.RootApplication;
import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.persistence.Disk;
import eu.stratuslab.storage.persistence.Disk.DiskType;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class DiskUtils {

    private static final Logger LOGGER = Logger.getLogger("org.restlet");

    // This defines one GibiByte (GiB, 2^30 bytes). GigaBytes (GB) is used
    // as a synonym for GiB throughout the code and in the API.
    public static final long BYTES_IN_GiB = 1024L * 1024L * 1024L;
    public static final double BYTES_IN_GiB_DOUBLE = (double) BYTES_IN_GiB;

    private DiskUtils() {

    }

    private static BackEndStorage getDiskStorage() {

        return new BackEndStorage();

    }

    public static String getTurl(String diskUuid) {
        BackEndStorage backend = getDiskStorage();
        return backend.getTurl(diskUuid);
    }

    public static void createDisk(Disk disk) {

        BackEndStorage diskStorage = getDiskStorage();

        diskStorage.create(disk.getUuid(), disk.getSize());
        diskStorage.map(disk.getUuid());

        disk.store();

    }

    public static Disk createMachineImageCoWDisk(Disk disk) {

        BackEndStorage diskStorage = getDiskStorage();

        Disk cowDisk = createCowDisk(disk);

        diskStorage.createCopyOnWrite(disk.getUuid(), cowDisk.getUuid(), disk.getSize());

        cowDisk.setType(DiskType.MACHINE_IMAGE_LIVE);
        diskStorage.map(cowDisk.getUuid());

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

    public static String rebaseDisk(Disk disk) {

        BackEndStorage diskStorage = getDiskStorage();

        return diskStorage.rebase(disk);
    }

    public static void removeDisk(String uuid) {
        getDiskStorage().unmap(uuid);
        getDiskStorage().delete(uuid);
    }

    public static String getDiskUri(String endpoint, String uuid) {
        try {
            URI endpointUri = URI.create(endpoint);
            String authority = endpointUri.getAuthority();
            String path = endpointUri.getPath();
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            URI diskUri = new URI("pdisk", authority, path, null, null);
            return diskUri.resolve(uuid).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static void attachHotplugDisk(String serviceEndpoint, String node, String vmId, String diskUuid,
                                         String target, String turl) {

        // Do NOT use the --register flag here. This may cause an infinite loop
        // in the process because it calls the pdisk service again.

        List<String> cmd = createHotPlugCommand(node);
        cmd.add("--op up");

        cmd.add("--attach");
        cmd.add("--mark");
        cmd.add("--link");
        cmd.add("--mount");

        cmd.add("--pdisk-id");
        cmd.add(getDiskUri(serviceEndpoint, diskUuid));

        cmd.add("--target");
        cmd.add(target);

        cmd.add("--vm-id");
        cmd.add(vmId);

        cmd.add("--turl");
        cmd.add(turl);

        cmd.add("--vm-disk-name");
        cmd.add(getDiskUri(serviceEndpoint, diskUuid));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        ProcessUtils.execute(pb, "Unable to attach persistent disk");
    }

    public static void detachHotplugDisk(String serviceEndpoint, String node, String vmId, String diskUuid,
                                         String target, String turl) {

        // Do NOT use the --register flag here. This may cause an infinite loop
        // in the process because it calls the pdisk service again.

        List<String> cmd = createHotPlugCommand(node);
        cmd.add("--op down");

        cmd.add("--attach");
        cmd.add("--mark");
        cmd.add("--link");
        cmd.add("--mount");

        cmd.add("--pdisk-id");
        cmd.add(getDiskUri(serviceEndpoint, diskUuid));

        cmd.add("--target");
        cmd.add(target);

        cmd.add("--vm-id");
        cmd.add(vmId);

        cmd.add("--turl");
        cmd.add(turl);

        cmd.add("--vm-disk-name");
        cmd.add(getDiskUri(serviceEndpoint, diskUuid));

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

    public static String calculateHash(File file) throws FileNotFoundException {

        InputStream fis = new FileInputStream(file);

        return calculateHash(fis);

    }

    public static String calculateHash(InputStream fis) throws FileNotFoundException {

        Map<String, BigInteger> info = MetadataUtils.streamInfo(fis);
        BigInteger sha1Digest = info.get("SHA-1");
        return MetadataUtils.sha1ToIdentifier(sha1Digest);
    }

    public static void createAndPopulateDiskLocal(Disk disk) {

        String uuid = disk.getUuid();

        File cachedDiskFile = FileUtils.getCachedDiskFile(uuid);
        String cachedDisk = cachedDiskFile.getAbsolutePath();
        try {

            createDisk(disk);

            try {
                copyContentsToVolume(uuid, cachedDisk);

                // Size has already been set on the disk.
                disk.setType(DiskType.DATA_IMAGE_RAW_READONLY);
                disk.setSeed(true);

            } catch (RuntimeException e) {
                removeDisk(disk.getUuid());
            }

        } finally {
            if (!cachedDiskFile.delete()) {
                LOGGER.warning("could not delete upload cache file: " + cachedDisk);
            }
        }
    }

    private static void copyContentsToVolume(String uuid, String cachedDisk) {
        String diskLocation = attachDiskToThisHost(uuid);
        try {
            FileUtils.copyFile(cachedDisk, diskLocation);
        } finally {
            detachDiskFromThisHost(uuid);
            getDiskStorage().unmap(uuid);
        }
    }

    public static Map<String, BigInteger> copyUrlToVolume(String uuid, String url) throws IOException {

        File diskLocation = new File(attachDiskToThisHost(uuid));
        try {
            return DownloadUtils.copyUrlContentsToFile(url, diskLocation);
        } finally {
            detachDiskFromThisHost(uuid);
            getDiskStorage().unmap(uuid);
        }
    }

    public static long convertBytesToGibiBytes(long sizeInBytes) {
        long inGiB = (long) Math.ceil(sizeInBytes / BYTES_IN_GiB_DOUBLE);
        return (inGiB <= 0 ? 1L : inGiB);
    }

    public static void createCompressedDisk(String uuid) {

        String diskLocation = attachDiskToThisHost(uuid);

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c",
                RootApplication.CONFIGURATION.GZIP_CMD + " -f -c " + diskLocation + " > " + getCompressedDiskLocation(
                        uuid));
        ProcessUtils.execute(pb, "Unable to compress disk " + uuid);

        detachDiskFromThisHost(uuid);
    }

    private static String attachDiskToThisHost(String uuid) {

        int port = ServiceConfiguration.getInstance().PDISK_SERVER_PORT;

        String linkName = getLinkedVolumeInDownloadCache(uuid);

        String turl = getTurl(uuid);

        List<String> cmd = getCommandAttachAndLinkLocal(uuid, "https://localhost:" + port + "/pdisk/pswd", linkName, turl);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        ProcessUtils.execute(pb, "Unable to attach persistent disk");

        return linkName;
    }

    private static void detachDiskFromThisHost(String uuid) {
        unlinkVolumeFromDownloadCache(uuid);

        int port = ServiceConfiguration.getInstance().PDISK_SERVER_PORT;

        BackEndStorage backend = getDiskStorage();
        String turl = backend.getTurl(uuid);

        List<String> cmd = getCommandDetachLocal(uuid, "https://localhost:" + port + "/pdisk/pswd", turl);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        ProcessUtils.execute(pb, "Unable to detach persistent disk");
    }

    private static void unlinkVolumeFromDownloadCache(String uuid) {
        String linkName = getLinkedVolumeInDownloadCache(uuid);
        File file = new File(linkName);
        if (!file.delete()) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Failed deleting linked file: " + linkName);
        }
    }

    private static List<String> getCommandAttachAndLinkLocal(String uuid, String endpoint, String linkName,
                                                             String turl) {
        List<String> cmd = new ArrayList<String>();

        cmd.add("/usr/sbin/stratus-pdisk-client.py");

        cmd.add("--op");
        cmd.add("up");

        cmd.add("--attach");

        cmd.add("--pdisk-id");
        cmd.add(getDiskUri(endpoint, uuid));

        cmd.add("--link-to");
        cmd.add(linkName);

        cmd.add("--turl");
        cmd.add(turl);

        return cmd;
    }

    private static List<String> getCommandDetachLocal(String uuid, String endpoint, String turl) {
        List<String> cmd = new ArrayList<String>();

        cmd.add("/usr/sbin/stratus-pdisk-client.py");

        cmd.add("--op");
        cmd.add("down");

        cmd.add("--attach");

        cmd.add("--pdisk-id");
        cmd.add(getDiskUri(endpoint, uuid));

        cmd.add("--turl");
        cmd.add(turl);

        return cmd;
    }

    private static String getLinkedVolumeInDownloadCache(String uuid) {
        return RootApplication.CONFIGURATION.CACHE_LOCATION + "/" + uuid + ".link";
    }

    public static String getCompressedDiskLocation(String uuid) {
        return RootApplication.CONFIGURATION.CACHE_LOCATION + "/" + uuid + ".gz";
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
