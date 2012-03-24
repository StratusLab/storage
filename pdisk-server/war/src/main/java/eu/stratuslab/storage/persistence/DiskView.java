package eu.stratuslab.storage.persistence;

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(name = "item")
public class DiskView {

	@Attribute
	private String uuid;

	private String tag;

	private long size;

	private int usersCount;

	private String owner;

	private String quarantine;

	private String identifier;

	public DiskView(String uuid, String tag, long size, int usersCount,
			String owner, String quarantine, String identifier) {
		this.uuid = uuid;
		this.tag = tag;
		this.size = size;
		this.usersCount = usersCount;
		this.owner = owner;
		this.quarantine = quarantine;
		this.setIdentifier(identifier);
	}

	@Root(name = "list")
	public static class DiskViewList {

		@SuppressWarnings("unused")
		@ElementList(inline = true, required = false)
		private final List<DiskView> list;

		public DiskViewList(List<DiskView> list) {
			this.list = list;
		}
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}

	public int getUsersCount() {
		return usersCount;
	}

	public void setUsersClount(int usersCont) {
		this.usersCount = usersCont;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getUuid() {
		return uuid;
	}

	public String getQuarantine() {
		return quarantine;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public String getIdentifier() {
		return identifier;
	}

}
