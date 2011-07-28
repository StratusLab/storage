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
package eu.stratuslab.storage.disk.utils;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper.States;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class DiskProperties {
	private ZooKeeper zk;

	// Property keys
	public static final String UUID_KEY = "uuid";
	public static final String DISK_OWNER_KEY = "owner";
	public static final String DISK_VISIBILITY_KEY = "visibility";
	public static final String DISK_CREATION_DATE_KEY = "created";	
	public static final String DISK_USERS_KEY = "users";
	
	public DiskProperties() {
		// ZooKeeper connection
		try {
			zk = new ZooKeeper(PersistentDiskApplication.ZK_ADDRESSES, 3000, null);
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
			if (zk.exists(PersistentDiskApplication.ZK_DISKS_PATH, false) == null) {
				zk.create(PersistentDiskApplication.ZK_DISKS_PATH,
						"pdiskDisks".getBytes(), Ids.OPEN_ACL_UNSAFE,
						CreateMode.PERSISTENT);
			}
			if (zk.exists(PersistentDiskApplication.ZK_USERS_PATH, false) == null) {
				zk.create(PersistentDiskApplication.ZK_USERS_PATH,
						"pdiskUsers".getBytes(), Ids.OPEN_ACL_UNSAFE,
						CreateMode.PERSISTENT);
			}
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	public List<String> getDisks() {
		List<String> disks = null;
		try {
			disks = zk.getChildren(PersistentDiskApplication.ZK_DISKS_PATH,
					false);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}

		return disks;
	}

	public void createNode(String path, String content) {
		try {
			zk.create(path, content.getBytes(), Ids.OPEN_ACL_UNSAFE,
					CreateMode.PERSISTENT);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	public void deleteDiskProperties(String root) {
		try {
			List<String> tree = listSubTree(root);
			for (int i = tree.size() - 1; i >= 0; --i) {
				zk.delete(tree.get(i), -1);
			}
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	private String getNode(String root) {
		String node = "";

		try {
			node = new String(zk.getData(root, false, null));
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}

		return node;
	}
	
	private void setNode(String root, String value) {
		try {
			zk.setData(root, value.getBytes(), -1);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}
	
	private void deleteNode(String root) {
		try {
			zk.delete(root, -1);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	private List<String> listSubTree(String pathRoot) {
		Deque<String> queue = new LinkedList<String>();
		List<String> tree = new ArrayList<String>();
		List<String> children;

		queue.add(pathRoot);
		tree.add(pathRoot);

		while (true) {
			String node = queue.pollFirst();
			if (node == null) {
				break;
			}

			try {
				children = zk.getChildren(node, false);
			} catch (KeeperException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"ZooKeeper error: " + e.getMessage());
			} catch (InterruptedException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						e.getMessage());
			}

			for (String child : children) {
				String childPath = node + "/" + child;
				queue.add(childPath);
				tree.add(childPath);
			}
		}
		return tree;
	}

	public Properties getDiskProperties(String root) {
		if (!diskExists(root)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Cannot load disk properties as it does not exists");
		}
		
		Properties properties = new Properties();
		List<String> tree = listSubTree(root);

		for (int i = tree.size() - 1; i >= 0; --i) {
			String key = PersistentDiskApplication.last(tree.get(i).split("/"));
			String content = getNode(tree.get(i));

			if (tree.get(i) == root) {
				key = UUID_KEY;
			}

			properties.put(key, content);
		}
		return properties;
	}

	private Boolean diskExists(String path) {
		try {
			return zk.exists(path, false) != null;
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}
	
	public int getDiskUsersNo(String path) {
		Properties diskProp = getDiskProperties(path);
		int users = 0;
		
		try {
			users = Integer.parseInt(diskProp.get(DiskProperties.DISK_USERS_KEY).toString());
		} catch (NumberFormatException e) {
			// Assume there's not user
		}

		return users;
	}
	
	private String getDiskUserPath(String userId) {
		return PersistentDiskApplication.ZK_USERS_PATH + "/" + userId;
	}
	
	private String getDiskPath(String uuid) {
		return PersistentDiskApplication.ZK_DISKS_PATH + "/" + uuid;
	}
	
	public void addDiskUser(String uuid, String userId) {
		String path = getDiskPath(uuid);
		int currentUsers = getDiskUsersNo(path);
		
		createNode(getDiskUserPath(userId), uuid);
		setNode(path + "/" + DISK_USERS_KEY, String.valueOf(currentUsers+1));
	}
	
	private String getUsedDisk(String userId) {
		return getNode(getDiskUserPath(userId));
	}
	
	public String removeDiskUser(String userId) {
		String uuid = getUsedDisk(userId);
		String path = getDiskPath(uuid);
		int currentUser = getDiskUsersNo(path);
		
		deleteNode(getDiskUserPath(userId));
		setNode(path + "/" + DISK_USERS_KEY, String.valueOf(currentUser-1));
		
		return uuid;
	}
	
	public int remainingFreeUser(String path) {
		return PersistentDiskApplication.USERS_PER_DISK - getDiskUsersNo(path);
	}
	
	public Boolean isUser(String userId) {
		Boolean user = false;
		
		try {
			zk.getData(getDiskUserPath(userId), false, null);
			user = true;
		} catch (KeeperException e) {
		} catch (InterruptedException e) {
		}
		
		return user;
	}
	
	public Boolean canAttachDisk(String path) {
		return getDiskUsersNo(path) < PersistentDiskApplication.USERS_PER_DISK;
	}

	public ZooKeeper getZooKeeper() {
		return zk;
	}
}
