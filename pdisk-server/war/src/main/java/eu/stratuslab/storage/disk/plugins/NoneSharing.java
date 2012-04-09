package eu.stratuslab.storage.disk.plugins;

public final class NoneSharing implements DiskSharing {

	public NoneSharing() {

	}

	public void preDiskCreationActions(String uuid) {

	}

	public void postDiskCreationActions(String uuid) {
	}

	public void preDiskRemovalActions(String uuid) {
	}

	public void postDiskRemovalActions(String uuid) {
	}

}
