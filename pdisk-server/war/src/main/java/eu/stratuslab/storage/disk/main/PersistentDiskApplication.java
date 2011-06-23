package eu.stratuslab.storage.disk.main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.LocalReference;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;

import eu.stratuslab.storage.disk.resources.DiskResource;
import eu.stratuslab.storage.disk.resources.DisksResource;
import eu.stratuslab.storage.disk.resources.ForceTrailingSlashResource;
import eu.stratuslab.storage.disk.resources.CreateResource;
import eu.stratuslab.storage.disk.resources.HomeResource;
import freemarker.template.Configuration;

public class PersistentDiskApplication extends Application {
	public enum DiskType {
		LVM, FILE;
	}

	public static final String CFG_FILENAME = "/etc/stratuslab/persistent-disk.cfg";
	public static final String LVCREATE_DIR = "/sbin";
	public static final String LVREMOVE_DIR = "/sbin";

	// ZooKeeper default configuration
	public static final String DEFAULT_ZK_ADDRESS = "127.0.0.1";
	public static final String DEFAULT_ZK_PORT = "2181";
	public static final String DEFAULT_ZK_ROOT_PATH = "/disks";

	// Disk creation (file and LVM)
	public static final String DEFAULT_DISK_TYPE = "file"; // Can be "lvm"|"file"
	public static final String DEFAULT_DISK_STORE = "/tmp/diskstore";
	public static final String DEFAULT_LVM_GROUP_NAME = "/dev/vg.02";

	public static final Properties CONFIGURATION;
	public static final File DISK_STORE;
	public static final String ZK_ADDRESS;
	public static final int ZK_PORT;
	public static final String ZK_ROOT_PATH;
	public static final DiskType DISK_TYPE;
	public static final String LVM_GROUPE_PATH;

	private Configuration freeMarkerConfiguration = null;

	static {
		CONFIGURATION = readConfigFile();
		DISK_STORE = getDiskStoreDirectory();
		ZK_ADDRESS = getConfigValue("disk.store.zk_address", DEFAULT_ZK_ADDRESS);
		ZK_PORT = Integer.parseInt(getConfigValue("disk.store.zk_port",
				DEFAULT_ZK_PORT));
		ZK_ROOT_PATH = getConfigValue("disk.store.zk_root_path",
				DEFAULT_ZK_ROOT_PATH);
		DISK_TYPE = getDiskType();
		LVM_GROUPE_PATH = getConfigValue("disk.store.lvm.group.name",
				DEFAULT_LVM_GROUP_NAME);
	}

	public PersistentDiskApplication() {

		setName("StratusLab Persistent Disk Server");
		setDescription("StratusLab server for persistent disk storage.");
		setOwner("StratusLab");
		setAuthor("Charles Loomis");

		getTunnelService().setUserAgentTunnel(true);
	}

	public static Properties readConfigFile() {
		File cfgFile = new File(CFG_FILENAME);
		Properties properties = new Properties();

		if (cfgFile.exists()) {
			FileReader reader = null;
			try {
				reader = new FileReader(cfgFile);
				properties.load(reader);
			} catch (IOException consumed) {
				// TODO: Log this error.
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException consumed) {
						// TODO: Log this error.
					}
				}
			}
		}

		return properties;
	}

	public static String getConfigValue(String key, String defaultValue) {
		String configValue = CONFIGURATION.getProperty(key, defaultValue);
		return configValue;
	}

	public static File getDiskStoreDirectory() {
		String diskStoreDir = getConfigValue("disk.store.dir",
				DEFAULT_DISK_STORE);

		return new File(diskStoreDir);
	}

	public static DiskType getDiskType() {

		String diskType = getConfigValue("disk.store.disk_type",
				DEFAULT_DISK_TYPE);

		if (diskType == "lvm")
			return DiskType.LVM;
		else
			return DiskType.FILE;
	}

	@Override
	public Restlet createInboundRoot() {
		Context context = getContext();

		freeMarkerConfiguration = createFreeMarkerConfig(context);

		Router router = new Router(context);

		router.attach("/disks/{uuid}/", DiskResource.class);
		router.attach("/disks/{uuid}", ForceTrailingSlashResource.class);

		router.attach("/disks/", DisksResource.class);
		router.attach("/disks", ForceTrailingSlashResource.class);

		// Defines a route for the upload form
		router.attach("/create/", CreateResource.class);
		router.attach("/create", ForceTrailingSlashResource.class);

		Directory cssDir = new Directory(getContext(), "war:///css");
		cssDir.setNegotiatingContent(false);
		cssDir.setIndexName("index.html");
		router.attach("/css/", cssDir);

		// Unknown root pages get the home page.
		router.attachDefault(HomeResource.class);

		return router;
	}

	private static Configuration createFreeMarkerConfig(Context context) {

		Configuration fmCfg = new Configuration();
		fmCfg.setLocalizedLookup(false);

		LocalReference fmBaseRef = LocalReference.createClapReference("/");
		fmCfg.setTemplateLoader(new ContextTemplateLoader(context, fmBaseRef));

		return fmCfg;
	}

	public Configuration getFreeMarkerConfiguration() {
		return freeMarkerConfiguration;
	}
}
