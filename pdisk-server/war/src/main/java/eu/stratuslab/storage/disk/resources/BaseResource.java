package eu.stratuslab.storage.disk.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.restlet.data.LocalReference;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ServerResource;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class BaseResource extends ServerResource implements Watcher {

	protected static final String UUID_KEY = "uuid";

	protected static final Logger LOGGER = Logger.getLogger("org.restlet");

	protected ZooKeeper zk;

	public BaseResource() {
		try {
			// Create a ZK global object
			zk = new ZooKeeper(PersistentDiskApplication.ZK_ADDRESS,
					PersistentDiskApplication.ZK_PORT, this);

			if (!zkPathExists(PersistentDiskApplication.ZK_ROOT)) {
				createZkNode(PersistentDiskApplication.ZK_ROOT, "pdisk");
			}
		} catch (IOException e) {
			LOGGER.severe("Unable to connect to ZooKeeper: " + e.getMessage());
		}
	}

	protected Representation templateRepresentation(String tpl) {
		LocalReference ref = LocalReference.createClapReference(tpl);
		return new ClientResource(ref).get();
	}

	protected Boolean zkPathExists(String path) {
		try {
			return zk.exists(path, false) != null;
		} catch (KeeperException e) {
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}

	protected String getZkNode(String root) throws KeeperException,
			InterruptedException {
		return new String(zk.getData(root, false, null));
	}

	public Properties loadZkProperties(String root) throws KeeperException,
			InterruptedException {
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

	protected void deleteRecursiveZkDiskProperties(String root) {
		try {
			List<String> tree = listZkSubTreeBFS(root);
			for (int i = tree.size() - 1; i >= 0; --i) {
				zk.delete(tree.get(i), -1);
			}
		} catch (KeeperException e) {
			LOGGER.severe("error removing znode: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error removing znode: " + e.getMessage());
		}
	}

	protected String buildZkDiskPath(String uuid) {
		return PersistentDiskApplication.ZK_ROOT + "/" + uuid;
	}

	protected List<String> listZkSubTreeBFS(final String pathRoot)
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
			List<String> children = zk.getChildren(node, false);
			for (final String child : children) {
				final String childPath = node + "/" + child;
				queue.add(childPath);
				tree.add(childPath);
			}
		}
		return tree;
	}

	protected void createZkNode(String path, String content) {
		try {
			zk.create(path, content.getBytes(), Ids.OPEN_ACL_UNSAFE,
					CreateMode.PERSISTENT);
		} catch (KeeperException e) {
			LOGGER.severe("error inserting znode: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error inserting znode: " + e.getMessage());
		}
	}

	public void process(WatchedEvent event) {

	}

	public static <T> T last(T[] array) {
		return array[array.length - 1];
	}
}
