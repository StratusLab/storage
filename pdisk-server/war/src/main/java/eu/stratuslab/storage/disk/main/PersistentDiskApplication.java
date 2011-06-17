package eu.stratuslab.storage.disk.main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;

import eu.stratuslab.storage.disk.resources.DiskResource;
import eu.stratuslab.storage.disk.resources.DisksResource;
import eu.stratuslab.storage.disk.resources.ForceTrailingSlashResource;
import eu.stratuslab.storage.disk.resources.CreateResource;

public class PersistentDiskApplication extends Application {

    public static final String CFG_FILENAME = "/etc/stratuslab/persistent-disk.cfg";

    public static final String DEFAULT_DISK_STORE = "/tmp/diskstore";

    public static final String ZK_ADDRESS = "127.0.0.1";
    public static final int ZK_PORT = 2181;
    public static final String ZK_ROOT = "/disks";
    
    public static final File DISK_STORE;

    static {
        DISK_STORE = getDiskStoreDirectory();
    }

    public PersistentDiskApplication() {

        setName("StratusLab Persistent Disk Server");
        setDescription("StratusLab server for persistent disk storage.");
        setOwner("StratusLab");
        setAuthor("Charles Loomis");

        getTunnelService().setUserAgentTunnel(true);
    }

    public static File getDiskStoreDirectory() {

        String diskStoreDir = DEFAULT_DISK_STORE;

        File cfgFile = new File(CFG_FILENAME);
        if (cfgFile.exists()) {
            Properties properties = new Properties();
            FileReader reader = null;
            try {
                reader = new FileReader(cfgFile);
                properties.load(reader);
            } catch (IOException consumed) {
                // TODO: Log this error.
            } finally {
                if (reader!=null) {
                    try {
                        reader.close();
                    } catch (IOException consumed) {
                        // TODO: Log this error.
                    }
                }
            }
            diskStoreDir = properties.getProperty("disk.store.dir", DEFAULT_DISK_STORE);
        }

        return new File(diskStoreDir);
    }

    @Override
    public Restlet createInboundRoot() {

        Router router = new Router(getContext());

        Directory indexDir = new Directory(getContext(), "war:///");
        indexDir.setNegotiatingContent(false);
        indexDir.setIndexName("index.html");

        router.attach("/disks/{uuid}/", DiskResource.class);
        router.attach("/disks/{uuid}", ForceTrailingSlashResource.class);

        router.attach("/disks/", DisksResource.class);
        router.attach("/disks", ForceTrailingSlashResource.class);

        // Defines a route for the upload form
        router.attach("/create/", CreateResource.class);
        router.attach("/create", ForceTrailingSlashResource.class);

        router.attach("/", indexDir);

        return router;
    }

}
