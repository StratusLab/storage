package eu.stratuslab.storage.disk.resources;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import freemarker.template.Configuration;

public class BaseResource extends ServerResource {

	protected static final String UUID_KEY = "uuid";

	protected static final Logger LOGGER = Logger.getLogger("org.restlet");

	public static final ZooKeeper ZK = initializeZooKeeper();
	
	private String username = "";

	private Configuration getFreeMarkerConfiguration() {
		return ((PersistentDiskApplication) getApplication())
				.getFreeMarkerConfiguration();
	}

	protected TemplateRepresentation createTemplateRepresentation(String tpl,
			Map<String, Object> info, MediaType mediaType) {

		Configuration freeMarkerConfig = getFreeMarkerConfiguration();

		return new TemplateRepresentation(tpl, freeMarkerConfig, info,
				mediaType);
	}
	
	protected Map<String, Object> createInfoStructure(String title) {

        Map<String, Object> info = new HashMap<String, Object>();

        // Add the standard base URL declaration.
        info.put("baseurl", getApplicationBaseUrl());

        // Add the title if appropriate.
        if (title != null && !"".equals(title)) {
            info.put("title", title);
        }
        
        // Add user name information
        info.put("username", getUsername());
        	
        return info;
    }
	
	public String getUsername() {
		if (!username.isEmpty()) {
			return username;
		}
		
        ChallengeResponse cr = getRequest().getChallengeResponse();

        if (cr == null) {
        	username = "UNKNOWN";
        }
        else {
        	username = cr.getIdentifier();
        }
    	
        return username;
	}
	
	protected String getApplicationBaseUrl() {
		return getRequest().getRootRef().toString();
	}

	public static List<String> getDisks() throws KeeperException,
			InterruptedException {
		List<String> disks = ZK.getChildren(
				PersistentDiskApplication.ZK_ROOT_PATH, false);

		return disks;
	}

	private static ZooKeeper initializeZooKeeper() {
		ZooKeeper zk = null;

		try {
			zk = new ZooKeeper(PersistentDiskApplication.ZK_ADDRESS,
					PersistentDiskApplication.ZK_PORT, null);
		} catch (Exception e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to connect to ZooKeeper: " + e.getMessage());
		}

		try {
			while (zk.getState() == States.CONNECTING) {
				Thread.sleep(20);
			}

			if (zk.getState() != States.CONNECTED) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"Unable to connect to ZooKeeper");
			}
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to connect to ZooKeeper: " + e.getMessage());
		}

		try {
			if (zk.exists(PersistentDiskApplication.ZK_ROOT_PATH, false) == null) {
				zk.create(PersistentDiskApplication.ZK_ROOT_PATH,
						"pdisk".getBytes(), Ids.OPEN_ACL_UNSAFE,
						CreateMode.PERSISTENT);
			}
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		}

		return zk;
	}

	protected static String buildZkDiskPath(String uuid) {
		return PersistentDiskApplication.ZK_ROOT_PATH + "/" + uuid;
	}

	protected static void createZkNode(String path, String content) {
		try {
			ZK.create(path, content.getBytes(), Ids.OPEN_ACL_UNSAFE,
					CreateMode.PERSISTENT);
		} catch (KeeperException e) {
			LOGGER.severe("error inserting znode: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error inserting znode: " + e.getMessage());
		}
	}

	protected static void deleteRecursiveZkDiskProperties(String root) {
		try {
			List<String> tree = listZkSubTreeBFS(root);
			for (int i = tree.size() - 1; i >= 0; --i) {
				ZK.delete(tree.get(i), -1);
			}
		} catch (KeeperException e) {
			LOGGER.severe("error removing znode: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error removing znode: " + e.getMessage());
		}
	}

	protected static String getZkNode(String root) throws KeeperException,
			InterruptedException {
		return new String(ZK.getData(root, false, null));
	}

	protected static List<String> listZkSubTreeBFS(final String pathRoot)
			throws KeeperException, InterruptedException {
		Deque<String> queue = new LinkedList<String>();
		List<String> tree = new ArrayList<String>();

		queue.add(pathRoot);
		tree.add(pathRoot);

		while (true) {
			String node = queue.pollFirst();
			if (node == null) {
				break;
			}
			List<String> children = ZK.getChildren(node, false);
			for (final String child : children) {
				final String childPath = node + "/" + child;
				queue.add(childPath);
				tree.add(childPath);
			}
		}
		return tree;
	}

	protected static Properties loadZkProperties(String root)
			throws KeeperException, InterruptedException {
		Properties properties = new Properties();
		List<String> tree = listZkSubTreeBFS(root);

		for (int i = tree.size() - 1; i >= 0; --i) {
			String key = last(tree.get(i).split("/"));
			String content = getZkNode(tree.get(i));

			if (tree.get(i) == root) {
				key = UUID_KEY;
			}

			properties.put(key, content);
		}
		return properties;
	}

	protected static Boolean zkPathExists(String path) {
		try {
			return ZK.exists(path, false) != null;
		} catch (KeeperException e) {
			LOGGER.severe("ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("ZooKeeper error: " + e.getMessage());
		}

		return false;
	}

	protected static <T> T last(T[] array) {
		return array[array.length - 1];
	}
	
    protected String getCurrentUrl() {
    	return getRequest().getResourceRef().toString();
    }
}
