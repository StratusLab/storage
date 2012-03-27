package eu.stratuslab.storage.persistence;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapKey;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Query;

import org.simpleframework.xml.ElementMap;

import eu.stratuslab.storage.disk.resources.BaseResource.DiskVisibility;
import eu.stratuslab.storage.disk.utils.DiskUtils;

@Entity
@SuppressWarnings("serial")
@NamedQueries({
		@NamedQuery(name = "allDisks", query = "SELECT NEW eu.stratuslab.storage.persistence.DiskView(d.uuid, d.tag, d.size, d.usersCount, d.owner, d.quarantine, d.identifier) FROM Disk d ORDER BY d.created DESC"),
		@NamedQuery(name = "allDisksByUser", query = "SELECT NEW eu.stratuslab.storage.persistence.DiskView(d.uuid, d.tag, d.size, d.usersCount, d.owner, d.quarantine, d.identifier) FROM Disk d WHERE d.owner = :user ORDER BY d.created DESC"),
		@NamedQuery(name = "allDisksAvailableByUser", query = "SELECT NEW eu.stratuslab.storage.persistence.DiskView(d.uuid, d.tag, d.size, d.usersCount, d.owner, d.quarantine, d.identifier) FROM Disk d WHERE d.owner = :user OR d.visibility = PUBLIC ORDER BY d.created DESC"),
		@NamedQuery(name = "allDisksByIdentifier", query = "SELECT NEW eu.stratuslab.storage.persistence.DiskView(d.uuid, d.tag, d.size, d.usersCount, d.owner, d.quarantine, d.identifier) FROM Disk d WHERE d.identifier = :identifier AND d.owner = :user ORDER BY d.created DESC") })
public class Disk implements Serializable {

	public static final String STATIC_DISK_TARGET = "static";
    public static final String UUID_KEY = "uuid";
    public static final String DISK_VISIBILITY_KEY = "visibility";
	public static final String DISK_SIZE_KEY = "size";
	public static final Object DISK_IDENTIFER_KEY = "Marketplace_id";

	public static Disk load(String uuid) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Disk disk = em.find(Disk.class, uuid);
		em.close();
		return disk;
	}

	public Disk store() {
		EntityManager em = PersistenceUtil.createEntityManager();
		EntityTransaction transaction = em.getTransaction();
		transaction.begin();
		Disk obj = em.merge(this);
		transaction.commit();
		em.close();
		return obj;
	}

	public void remove() {
		remove(getUuid());
	}

	public static void remove(String uuid) {
		EntityManager em = PersistenceUtil.createEntityManager();
		EntityTransaction transaction = em.getTransaction();
		transaction.begin();
		Disk fromDb = em.find(Disk.class, uuid);
		if (fromDb != null) {
			em.remove(fromDb);
		}
		transaction.commit();
		em.close();
	}

	@SuppressWarnings("unchecked")
	public static List<DiskView> listAll() {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("allDisks");
		List<DiskView> list = q.getResultList();
		em.close();
		return list;
	}

	@SuppressWarnings("unchecked")
	public static List<DiskView> listAllByUser(String user) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("allDisksByUser");
		q.setParameter("user", user);
		List<DiskView> list = q.getResultList();
		em.close();
		return list;
	}

	@SuppressWarnings("unchecked")
	public static boolean identifierExists(String identifier, String user) {
		if("".equals(identifier)) {
			return false;
		}
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("allDisksByIdentifier");
		q.setParameter("identifier", identifier);
		q.setParameter("user", user);
		List<DiskView> list = q.getResultList();
		return list.size() > 0;
	}

	public Disk() {
		uuid = DiskUtils.generateUUID();
	}

	@Id
	private String uuid;

	private String owner = "";
	private DiskVisibility visibility = DiskVisibility.PRIVATE;
	private String created;
	private int usersCount;
	private boolean isreadonly = false;
	private boolean iscow = false;
	private String tag = "";
	private long size = -1;
	private String quarantine = ""; // quarantine start date

	private String identifier = ""; // Marketplace identifier

	private boolean seed = false; // original... don't delete!
	
	private String baseDiskUuid;
	
	@MapKey(name = "id")
	@OneToMany(mappedBy = "disk", fetch=FetchType.EAGER)
	@ElementMap(name = "mounts", required = false, data = true, valueType = Mount.class)
	private Map<String, Mount> mounts = new HashMap<String, Mount>(); // key is vmId

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public DiskVisibility getVisibility() {
		return visibility;
	}

	public void setVisibility(DiskVisibility visibility) {
		this.visibility = visibility;
	}

	public String getCreated() {
		return created;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public int getUsersCount() {
		return usersCount;
	}

	public void setUsersCount(int count) {
		this.usersCount = count;
	}

	public boolean getIsreadonly() {
		return isreadonly;
	}

	public void setIsreadonly(boolean isreadonly) {
		this.isreadonly = isreadonly;
	}

	public boolean getIscow() {
		return iscow;
	}

	public void setIscow(boolean iscow) {
		this.iscow = iscow;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public int incrementUserCount() {
		EntityManager em = PersistenceUtil.createEntityManager();
		EntityTransaction transaction = em.getTransaction();
		transaction.begin();
		Disk disk = em.merge(this);
		disk.setUsersCount(disk.getUsersCount() + 1);
		int count = disk.getUsersCount();
		transaction.commit();
		em.close();
		return count;
	}

	public int decrementUserCount() {
		EntityManager em = PersistenceUtil.createEntityManager();
		EntityTransaction transaction = em.getTransaction();
		transaction.begin();
		Disk disk = em.merge(this);
		disk.setUsersCount(disk.getUsersCount() - 1);
		int count = disk.getUsersCount();
		transaction.commit();
		em.close();
		return count;
	}

	public void setBaseDiskUuid(String uuid) {
		this.baseDiskUuid = uuid;
	}

	public String getBaseDiskUuid() {
		return baseDiskUuid;
	}

	public int getMountsCount() {
		return mounts.size();
	}

	public String diskTarget(String mountId) {

		if (mounts.containsKey(mountId)) {
			return STATIC_DISK_TARGET;
		}

		return mounts.get(mountId).getDevice();
	}

	public Map<String, Mount> getMounts() {
		return mounts;
	}

	public String getQuarantine() {
		return quarantine;
	}

	public void setQuarantine(String quarantineStartDate) {
		this.quarantine = quarantineStartDate;
	}

	public void setSeed(boolean isSeed) {
		this.seed = isSeed;
	}

	public boolean isSeed() {
		return seed;
	}

}
