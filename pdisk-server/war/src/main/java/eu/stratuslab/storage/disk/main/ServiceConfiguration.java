package eu.stratuslab.storage.disk.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Properties;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.utils.FileUtils;

public class ServiceConfiguration {

    private static final ServiceConfiguration instance = new ServiceConfiguration();

    // Configuration file
    public static final String PDISK_SERVER_PORT_PARAM_NAME = "disk.store.server.port";
    public final int PDISK_SERVER_PORT;
    public static final String PDISK_SERVER_PORT_DEFAULT = "8445";
    public static final String DEFAULT_CFG_FILENAME = "pdisk.cfg";
    public static final String DEFAULT_CFG_LOCATION = "/etc/stratuslab/";

    // Disk size limits (in GiBs)
    public static final int DISK_SIZE_MIN = 1;
    public final int DISK_SIZE_MAX;
    public static final int DISK_SIZE_MAX_DEFAULT = 1024;

    // Download buffer size in Bytes.
    public final int DOWNLOAD_STREAM_BUFFER_SIZE;

    public static final int CACHE_EXPIRATION_DURATION = 2000;

    public final Properties CONFIGURATION;

    public final String CLOUD_NODE_SSH_KEY;
    public final String CLOUD_NODE_ADMIN;
    public final String CLOUD_NODE_VM_DIR;
    public final String CLOUD_SERVICE_USER;

    public final String CACHE_LOCATION;

    public final String GZIP_CMD;

    public final String[] BACKEND_PROXIES;

    public final int UPLOAD_COMPRESSED_IMAGE_MAX_BYTES;

    // ISCI
    public final int ISCSI_MAX_LUN;
    private static final String ISCSI_MAX_LUN_PARAM_NAME="disk.store.iscsi.max.lun";
    private static final String ISCSI_MAX_LUN_DEFAULT = "505";
    
    public final int ISCSI_CHECKER_UPDATE_SLEEP; 
    private static final String ISCSI_CHECKER_UPDATE_SLEEP_PARAM_NAME = "disk.store.iscsi.checker.update.sleep";
    private static final String ISCSI_CHECKER_UPDATE_SLEEP_DEFAULT = "60000";
    
    public final int CHOOSER_MIN_RETRY_WAIT_MS;
    private static final String CHOOSER_MIN_RETRY_WAIT_MS_PARAM_NAME = "disk.store.iscsi.chooser.min.retry.wait.ms";
    private static final String CHOOSER_MIN_RETRY_WAIT_MS_DEFAULT = "500";
    
    public final int CHOOSER_MAX_RETRY_WAIT_MS; 
    private static final String CHOOSER_MAX_RETRY_WAIT_MS_PARAM_NAME = "disk.store.iscsi.chooser.max.retry.wait.ms";
    private static final String CHOOSER_MAX_RETRY_WAIT_MS_DEFAULT = "1200";
    
    private ServiceConfiguration() {    	
        CONFIGURATION = readConfigFile();
        
        PDISK_SERVER_PORT = Integer
                .parseInt(getConfigValue(PDISK_SERVER_PORT_PARAM_NAME, PDISK_SERVER_PORT_DEFAULT));

        CLOUD_NODE_SSH_KEY = getConfigValue("disk.store.cloud.node.ssh_keyfile");
        CLOUD_NODE_ADMIN = getConfigValue("disk.store.cloud.node.admin");
        CLOUD_NODE_VM_DIR = getConfigValue("disk.store.cloud.node.vm_dir");
        CLOUD_SERVICE_USER = getConfigValue("disk.store.cloud.service.user");

        BACKEND_PROXIES = getBackendProxies();

        DISK_SIZE_MAX = getDiskSizeMax();

        CACHE_LOCATION = getCacheLocation();
        
        GZIP_CMD = getCommand("disk.store.utils.gzip");

        UPLOAD_COMPRESSED_IMAGE_MAX_BYTES = 10240000;

        DOWNLOAD_STREAM_BUFFER_SIZE = getDownloadStreamBufferSize();
        
        ISCSI_MAX_LUN = Integer.parseInt(getConfigValue(ISCSI_MAX_LUN_PARAM_NAME, ISCSI_MAX_LUN_DEFAULT));
		ISCSI_CHECKER_UPDATE_SLEEP = Integer.parseInt(getConfigValue(
				ISCSI_CHECKER_UPDATE_SLEEP_PARAM_NAME,
				ISCSI_CHECKER_UPDATE_SLEEP_DEFAULT));
		
		CHOOSER_MIN_RETRY_WAIT_MS = Integer.parseInt(getConfigValue(
				CHOOSER_MIN_RETRY_WAIT_MS_PARAM_NAME,
				CHOOSER_MIN_RETRY_WAIT_MS_DEFAULT));
		CHOOSER_MAX_RETRY_WAIT_MS = Integer.parseInt(getConfigValue(
				CHOOSER_MAX_RETRY_WAIT_MS_PARAM_NAME,
				CHOOSER_MAX_RETRY_WAIT_MS_DEFAULT));		
    }

	private int getDownloadStreamBufferSize() {
        try {
        	return Integer.valueOf(getConfigValue("disk.store.download.stream.buffer.size"));
        } catch (ResourceException ex) {
        	return 0;
        } catch (NumberFormatException ex) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "disk.store.size.max configuration property should be an integer.");
        }
    }

	public static ServiceConfiguration getInstance() {
        return instance;
    }

    private static Properties readConfigFile() {    	
        File cfgFile = locateConfigFile();      
        Properties properties = new Properties();

        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(cfgFile),
                    Charset.defaultCharset());
            properties.load(reader);
        } catch (IOException consumed) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "An error occured while reading configuration file");
        } finally {
            FileUtils.closeRaisingError(reader, cfgFile.getAbsolutePath());
        }

        return properties;
    }

    private static File locateConfigFile() {
    	
    	File cfgFile = null;
    	try {    		
        	cfgFile = new File(System.getProperty("pdisk.config.filename", null));        	
        	if (cfgFile.exists()) {
        		return cfgFile;
        	}
    	} catch (NullPointerException ex){
    		//
    	}

        // Default to a local filename, then one in the /etc/stratuslab/ area.
        cfgFile = new File(DEFAULT_CFG_FILENAME);
        if (!cfgFile.exists()) {
            cfgFile = new File(DEFAULT_CFG_LOCATION + DEFAULT_CFG_FILENAME);
            if (!cfgFile.exists()) {
                throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                        "Configuration file does not exists.");
            }
        }
        return cfgFile;
    }

    private String getConfigValue(String key) {
        if (!CONFIGURATION.containsKey(key)) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "Unable to retrieve configuration key: " + key);
        }

        return CONFIGURATION.getProperty(key);
    }

    private String getConfigValue(String key, String defaultValue) {
    	try {
    		return getConfigValue(key);
    	} catch (ResourceException ex) {
    		return defaultValue;
    	}
    }

    private String getCacheLocation() {
        String cache = getConfigValue("disk.store.cache.location");
        File cacheDir = new File(cache);

        if (cacheDir.exists()) {
            if (!cacheDir.isDirectory()) {
                throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                        "Cache location " + cacheDir.getAbsolutePath()
                                + " already in use");
            } else if (!cacheDir.canWrite()) {
                throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                        "Cannot write cache location "
                                + cacheDir.getAbsolutePath());
            }
        } else {
            if (!cacheDir.mkdirs()) {
                throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                        "Unable to create cache location "
                                + cacheDir.getAbsolutePath());
            }
        }

        return cache;
    }

    private String getCommand(String configName) {
        String configValue = getConfigValue(configName);
        File exec = new File(configValue);

        if (!FileUtils.isExecutable(exec)) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    configValue
                            + " command does not exist or is not executable");
        }

        return configValue;
    }

    private String[] getBackendProxies() {
    	String[] backendProxies = getConfigValue("disk.store.iscsi.proxies").split(",");
    	if (backendProxies.length == 0) {
    		throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
    				"disk.store.iscsi.proxies should not be empty.");
    	}
    	return backendProxies;
    }

    private int getDiskSizeMax() {
        try {
        	return Integer.valueOf(getConfigValue("disk.store.size.max"));
        } catch (ResourceException ex) {
        	return DISK_SIZE_MAX_DEFAULT;
        } catch (NumberFormatException ex) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "disk.store.size.max configuration property should be an integer.");
        }
    }

}
