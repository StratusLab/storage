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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.RootApplication;

public class DiskProperties implements Closeable {

    private ZooKeeper zk = null;

    private static final String DEVICE_PREFIX = "vd";

    // Property keys
    public static final String UUID_KEY = "uuid";
    public static final String DISK_OWNER_KEY = "owner";
    public static final String DISK_VISIBILITY_KEY = "visibility";
    public static final String DISK_CREATION_DATE_KEY = "created";
    public static final String DISK_USERS_KEY = "users";

    public static final String STATIC_DISK_TARGET = "static";
    public static final String DISK_TARGET_LIMIT = "limit";

    public DiskProperties() {
        connect(RootApplication.CONFIGURATION.ZK_ADDRESSES, 3000);
    }

    public void close() {
        disconnect();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    private void disconnect() {
        if (zk != null) {
            boolean closed = false;
            while (!closed) {
                try {
                    zk.close();
                    closed = true;
                } catch (InterruptedException consumed) {
                }
            }
        }
    }

    private void connect(String addresses, int timeout) {

        try {
            zk = new ZooKeeper(addresses, timeout, null);
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
            if (zk.exists(RootApplication.CONFIGURATION.ZK_DISKS_PATH, false) == null) {
                zk.create(RootApplication.CONFIGURATION.ZK_DISKS_PATH,
                        "pdiskDisks".getBytes(), Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }
            if (zk.exists(RootApplication.CONFIGURATION.ZK_USAGE_PATH, false) == null) {
                zk.create(RootApplication.CONFIGURATION.ZK_USAGE_PATH,
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

    private void createNode(String path, String content) {
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

    public void deleteRecursively(String root) {
        List<String> tree = listSubTree(root);
        for (int i = tree.size() - 1; i >= 0; --i) {
            deleteNode(tree.get(i));
        }
    }

    public List<String> getDisks() {
        return getChildren(RootApplication.CONFIGURATION.ZK_DISKS_PATH);
    }

    public void saveDiskProperties(String diskRoot, Properties properties) {
        Enumeration<?> propertiesEnum = properties.propertyNames();

        createNode(diskRoot, properties.get(UUID_KEY).toString());

        while (propertiesEnum.hasMoreElements()) {
            String key = (String) propertiesEnum.nextElement();
            String content = properties.getProperty(key);

            if (key != UUID_KEY) {
                createNode(diskRoot + "/" + key, content);
            }
        }
    }

    public Properties getDiskProperties(String root) {
        Properties properties = new Properties();
        List<String> tree = listSubTree(root);

        for (int i = tree.size() - 1; i >= 0; --i) {
            String key = MiscUtils.last(tree.get(i).split("/"));
            String content = getNode(tree.get(i));

            if (tree.get(i) == root) {
                key = UUID_KEY;
            }

            properties.put(key, content);
        }
        return properties;
    }

    public Boolean pathExists(String path) {
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

    public void addDiskMount(String node, String vmId, String uuid,
            String target, Logger logger) {

        String diskMountPath = getDiskMountPath(uuid);

        if (!pathExists(diskMountPath)) {
            String timestamp = MiscUtils.getTimestamp();
            logger.info("Creating node: " + diskMountPath + " " + timestamp);
            createNode(diskMountPath, timestamp);
        }

        String mountPath = getMountPath(node, vmId, uuid);

        if (pathExists(mountPath)) {
            String msg = String.format(
                    "disk %s is already mounted on VM %s on node %s", uuid,
                    vmId, node);
            throw new ResourceException(Status.CLIENT_ERROR_CONFLICT, msg);
        }

        logger.info("Creating node: " + mountPath + ", " + target);
        createNode(mountPath, target);

        logger.info("add disk usage path: " + mountPath + ", "
                + pathExists(mountPath) + ", " + target);
    }

    public void removeDiskMount(String node, String vmId, String uuid,
            Logger logger) {

        String mountPath = getMountPath(node, vmId, uuid);

        logger.info("Deleting node: " + mountPath + ", "
                + pathExists(mountPath));

        if (pathExists(mountPath)) {
            deleteNode(mountPath);
        } else {
            String msg = String.format(
                    "disk %s is not mounted on VM %s on node %s", uuid, vmId,
                    node);
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, msg);
        }
    }

    private String getDiskMountPath(String uuid) {
        return String.format("%s/%s",
                RootApplication.CONFIGURATION.ZK_USAGE_PATH, uuid);
    }

    private String getMountPath(String node, String vmId, String uuid) {
        return String.format("%s/%s-%s", getDiskMountPath(uuid), vmId, node);
    }

    public int getNumberOfMounts(String uuid) {
        List<String> mounts = getDiskMountIds(uuid);
        return mounts.size();
    }

    public List<String> getDiskMountIds(String uuid) {

        List<String> results = Collections.emptyList();

        String diskMountPath = getDiskMountPath(uuid);
        if (pathExists(diskMountPath)) {
            results = getChildren(diskMountPath);
        }

        return results;
    }

    public List<Properties> getDiskMounts(String uuid) {

        List<Properties> results = Collections.emptyList();

        String diskMountPath = getDiskMountPath(uuid);

        for (String mount : getDiskMountIds(uuid)) {
            String childPath = diskMountPath + "/" + mount;
            String value = getNode(childPath);

            Properties properties = new Properties();
            properties.put("uuid", uuid);
            properties.put("mountid", mount);
            properties.put("device", value);
            results.add(properties);
        }

        return results;
    }

    public String diskTarget(String node, String vmId, String uuid) {

        String mountPath = getMountPath(node, vmId, uuid);

        if (!pathExists(mountPath)) {
            return STATIC_DISK_TARGET;
        }

        return getNode(mountPath);
    }

    public String nextHotpluggedDiskTarget(String node, String vmId, String uuid) {

        char newDriveLetter = 'a';

        String mountPath = getMountPath(node, vmId, uuid);

        if (pathExists(mountPath)) {

            String device = getNode(mountPath);
            if (!STATIC_DISK_TARGET.equals(device)) {
                newDriveLetter = device.charAt(device.length() - 1);
                newDriveLetter++;

                if (newDriveLetter > 'z') {
                    return DISK_TARGET_LIMIT;
                }
            }
        }

        return DEVICE_PREFIX + newDriveLetter;
    }

    public int remainingFreeUser(String path) {
        return RootApplication.CONFIGURATION.USERS_PER_DISK
                - getNumberOfMounts(path);
    }

    private List<String> listSubTree(String pathRoot) {
        Deque<String> queue = new LinkedList<String>();
        List<String> tree = new ArrayList<String>();

        queue.add(pathRoot);
        tree.add(pathRoot);

        while (true) {
            String node = queue.pollFirst();
            if (node == null) {
                break;
            }

            for (String child : getChildren(node)) {
                String childPath = node + "/" + child;
                queue.add(childPath);
                tree.add(childPath);
            }
        }
        return tree;
    }

    private List<String> getChildren(String path) {

        List<String> children = Collections.emptyList();

        try {
            children = zk.getChildren(path, false);
        } catch (KeeperException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    "ZooKeeper error: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                    e.getMessage());
        }

        return children;
    }
}
