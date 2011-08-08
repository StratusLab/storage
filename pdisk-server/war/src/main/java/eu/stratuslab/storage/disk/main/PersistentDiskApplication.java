/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.main;

import static org.restlet.data.MediaType.APPLICATION_WWW_FORM;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.LocalReference;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.representation.Representation;
import org.restlet.resource.Directory;
import org.restlet.resource.ResourceException;
import org.restlet.routing.Router;
import org.restlet.security.ChallengeAuthenticator;

import eu.stratuslab.storage.disk.resources.ActionResource;
import eu.stratuslab.storage.disk.resources.DiskResource;
import eu.stratuslab.storage.disk.resources.DisksResource;
import eu.stratuslab.storage.disk.resources.ForceTrailingSlashResource;
import eu.stratuslab.storage.disk.resources.CreateResource;
import eu.stratuslab.storage.disk.resources.HomeResource;
import eu.stratuslab.storage.disk.resources.NotFoundResource;
import eu.stratuslab.storage.disk.utils.DumpVerifier;
import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;
import freemarker.template.Configuration;

public class PersistentDiskApplication extends Application {

	public enum DiskType {
		LVM, FILE;
	}
	
	public enum ShareType {
		ISCSI, NFS;
	}

	// Configuration file
	public static final String CFG_FILENAME = "/etc/stratuslab/pdisk.cfg";
	public static final String ISCSI_CONFIG_FILENAME = "/etc/stratuslab/iscsi.conf";
	
	// Disk size limits (in GiBs)
	public static final int DISK_SIZE_MIN = 1;
	public static final int DISK_SIZE_MAX = 1024;
	
	public final static String RESPONSE_SUCCESS = "1";
	public final static String RESPONSE_FAILLED = "0";
	

	// TODO: Move configuration stuff into separate class
	public static final Properties CONFIGURATION;

	public static final ShareType SHARE_TYPE;
	
	public static final String ZK_ADDRESSES;
	public static final String ZK_DISKS_PATH;
	public static final String ZK_USAGE_PATH;

	public static final DiskType ISCSI_DISK_TYPE;
	public static final File ISCSI_CONFIG;
	public static final String ISCSI_ADMIN;
	
	public static final String LVM_GROUPE_PATH;
	public static final String VGDISPLAY_CMD;
	public static final String LVCREATE_CMD;
	public static final String LVREMOVE_CMD;
	
	public static final File STORAGE_LOCATION;
	
	public static final int USERS_PER_DISK;
	
	public static final String CLOUD_NODE_SSH_KEY;
	public static final String CLOUD_NODE_ADMIN;
	public static final String CLOUD_NODE_VM_DIR;

	private Configuration freeMarkerConfiguration = null;
	
	static {
		CONFIGURATION = readConfigFile();
		
		SHARE_TYPE = getShareType();
		
		ZK_ADDRESSES = getConfigValue("disk.store.zookeeper.address");
		ZK_DISKS_PATH = getConfigValue("disk.store.zookeeper.disks");
		ZK_USAGE_PATH = getConfigValue("disk.store.zookeeper.usage");
		
		VGDISPLAY_CMD = getCommand("disk.store.lvm.vgdisplay");
		LVCREATE_CMD = getCommand("disk.store.lvm.create");
		LVREMOVE_CMD = getCommand("disk.store.lvm.remove");
		LVM_GROUPE_PATH = getLVMGroup();
		
		ISCSI_DISK_TYPE = getDiskType();
		ISCSI_CONFIG = getISCSIConfig();
		ISCSI_ADMIN = getCommand("disk.store.iscsi.admin");
		
		STORAGE_LOCATION = getDiskLocation();
		
		USERS_PER_DISK = getUsersPerDisks();
		
		CLOUD_NODE_SSH_KEY = getConfigValue("disk.store.cloud.node.ssh_keyfile");
		CLOUD_NODE_ADMIN = getConfigValue("disk.store.cloud.node.admin");
		CLOUD_NODE_VM_DIR = getConfigValue("disk.store.cloud.node.vm_dir");
	}
	

	public PersistentDiskApplication() {
		setName("StratusLab Persistent Disk Server");
		setDescription("StratusLab server for persistent disk storage.");
		setOwner("StratusLab");
		setAuthor("Charles Loomis");

		getTunnelService().setUserAgentTunnel(true);
	}

	private static Properties readConfigFile() {
		File cfgFile = new File(CFG_FILENAME);
		Properties properties = new Properties();

		if (!cfgFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Configuration file does not exists.");
		}

		FileReader reader = null;
		try {
			reader = new FileReader(cfgFile);
			properties.load(reader);
		} catch (IOException consumed) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"An error occured while reading configuration file");
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException consumed) {
					throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
							"An error occured while reading configuration file");
				}
			}
		}

		return properties;
	}
	
	private static ShareType getShareType() {
		String type = getConfigValue("disk.store.share");
		
		if (type.equalsIgnoreCase("nfs")) {
			return ShareType.NFS;
		} else if (type.equalsIgnoreCase("iscsi")) {
			return ShareType.ISCSI;
		} else {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Share type not recognised, has " + type);
		}
	}

	private static File getISCSIConfig() {
		String iscsiConf = getConfigValue("disk.store.iscsi.conf");
		File confHandler = new File(iscsiConf);
		File stratusConf = new File(ISCSI_CONFIG_FILENAME);
		String includeConfig = "\ninclude " + stratusConf.getAbsolutePath() + "\n";

		if (!confHandler.isFile()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to find ISCSI configuration file.");
		}

		// Add include instruction in conf file in not
		if (!FileUtils.fileHasLine(confHandler, includeConfig.replace("\n", ""))) {
			FileUtils.appendToFile(confHandler, includeConfig);
		}

		return stratusConf;
	}

	private static String getConfigValue(String key) {
		if (!CONFIGURATION.containsKey(key)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to retrieve configuration key: " + key);
		}

		return CONFIGURATION.getProperty(key);
	}

	private static File getDiskLocation() {
		String diskStoreDir;
		if (PersistentDiskApplication.SHARE_TYPE == ShareType.ISCSI) {
			diskStoreDir = getConfigValue("disk.store.iscsi.file.location");
		} else {
			diskStoreDir = getConfigValue("disk.store.nfs.location");
		}

		File diskStoreHandler = new File(diskStoreDir);

		// Don't check if we use LVM
		if (PersistentDiskApplication.SHARE_TYPE == ShareType.ISCSI 
				&& PersistentDiskApplication.ISCSI_DISK_TYPE == DiskType.LVM) {
			return diskStoreHandler;
		}

		if (!diskStoreHandler.isDirectory() || !diskStoreHandler.canWrite()
				|| !diskStoreHandler.canRead()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Disk store have to be readable and writable");
		}

		return diskStoreHandler;
	}

	private static DiskType getDiskType() {
		String diskType = getConfigValue("disk.store.iscsi.type");

		if (diskType.equalsIgnoreCase("lvm"))
			return DiskType.LVM;
		else if (diskType.equalsIgnoreCase("file"))
			return DiskType.FILE;
		else {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Invalid disk type configuration");
		}
	}
	
	private static String getCommand(String configName) {
		String configValue = getConfigValue(configName);
		File exec = new File(configValue);

		if (!ProcessUtils.isExecutable(exec)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					configValue + " commamd does not exists or not executable");
		}
		
		return configValue;
	}
	
	private static Configuration createFreeMarkerConfig(Context context) {

		Configuration fmCfg = new Configuration();
		fmCfg.setLocalizedLookup(false);

		LocalReference fmBaseRef = LocalReference.createClapReference("/");
		fmCfg.setTemplateLoader(new ContextTemplateLoader(context, fmBaseRef));

		return fmCfg;
	}

	private static String getLVMGroup() {
		String lvmGroup = getConfigValue("disk.store.lvm.device");
		
		checkLVMGroupExists(lvmGroup);

		return lvmGroup;
	}
	
	private static int getUsersPerDisks() {
		String users = getConfigValue("disk.store.user_per_disk");
		int userNo = 0;
		
		try {
			userNo = Integer.parseInt(users);
		} catch (NumberFormatException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to get user per disk value");
		}

		return userNo;
	}
	
	private static void checkLVMGroupExists(String lvmGroup) {
		ProcessBuilder pb = new ProcessBuilder(
				PersistentDiskApplication.VGDISPLAY_CMD, lvmGroup);

		ProcessUtils
				.execute(pb,
						"LVM Group does not exists. Please create it and restart pdisk service");
	}
	
	public static void checkEntity(Representation entity) {
		if (entity == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"post with null entity");
		}
	}

	public static void checkMediaType(MediaType mediaType) {
		if (!APPLICATION_WWW_FORM.equals(mediaType, true)) {
			throw new ResourceException(
					Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,
					mediaType.getName());
		}
	}
	
	public static String getDateTime() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();

		return dateFormat.format(date);
	}
	
	public static String join(List<String> list, String conjunction)
	{
	   StringBuilder sb = new StringBuilder();
	   boolean first = true;
	   for (String item : list)
	   {
	      if (first)
	         first = false;
	      else
	         sb.append(conjunction);
	      sb.append(item);
	   }
	   return sb.toString();
	}

	@Override
	public Restlet createInboundRoot() {
		Context context = getContext();

		freeMarkerConfiguration = createFreeMarkerConfig(context);

		// The guard is needed although JAAS which is doing the authentication
		// just to be able to retrieve client information (challenger).
		DumpVerifier verifier = new DumpVerifier();
		ChallengeAuthenticator guard = new ChallengeAuthenticator(getContext(),
				ChallengeScheme.HTTP_BASIC,
				"Stratuslab Persistent Disk Storage");
		guard.setVerifier(verifier);

		Router router = new Router(context);

		router.attach("/api/disks/{uuid}", DiskResource.class);
		router.attach("/disks/{uuid}/", DiskResource.class);
		router.attach("/disks/{uuid}", ForceTrailingSlashResource.class);

		router.attach("/api/disks", DisksResource.class);
		router.attach("/disks/", DisksResource.class);
		router.attach("/disks", ForceTrailingSlashResource.class);

		router.attach("/api/create", CreateResource.class);
		router.attach("/create/", CreateResource.class);
		router.attach("/create", ForceTrailingSlashResource.class);
		
		router.attach("/api/{action}/{uuid}", ActionResource.class);
		router.attach("/api/{action}", ActionResource.class);
		
//		router.attach("/logout/", LogoutResource.class);
//		router.attach("/logout", ForceTrailingSlashResource.class);

		router.attach("/", HomeResource.class);
		
		Directory cssDir = new Directory(getContext(), "war:///css");
		cssDir.setNegotiatingContent(false);
		cssDir.setIndexName("index.html");
		router.attach("/css/", cssDir);

		// Unknown root pages get the home page.
		router.attachDefault(NotFoundResource.class);

		guard.setNext(router);

		return guard;
	}

	public Configuration getFreeMarkerConfiguration() {
		return freeMarkerConfiguration;
	}

	public static <T> T last(T[] array) {
		return array[array.length - 1];
	}
	
}
