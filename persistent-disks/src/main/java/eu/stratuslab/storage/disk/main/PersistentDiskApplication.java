package eu.stratuslab.storage.disk.main;

import java.io.File;
import java.util.logging.Logger;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;

import eu.stratuslab.storage.disk.resources.DiskResource;
import eu.stratuslab.storage.disk.resources.DisksResource;
import eu.stratuslab.storage.disk.resources.ForceTrailingSlashResource;
import eu.stratuslab.storage.disk.resources.UploadResource;

public class PersistentDiskApplication extends Application {

    protected Logger logger = getLogger();

    public static final File diskStore = new File("/tmp/diskstore");

    public PersistentDiskApplication() {

        setName("StratusLab Persistent Disk Server");
        setDescription("StratusLab server for persistent disk storage.");
        setOwner("StratusLab");
        setAuthor("Charles Loomis");

        getTunnelService().setUserAgentTunnel(true);

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
        router.attach("/upload/", UploadResource.class);
        router.attach("/upload", ForceTrailingSlashResource.class);

        router.attach("/", indexDir);

        return router;
    }

}
