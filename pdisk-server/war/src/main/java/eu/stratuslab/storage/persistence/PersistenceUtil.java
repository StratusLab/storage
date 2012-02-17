package eu.stratuslab.storage.persistence;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class PersistenceUtil {

	private static final EntityManagerFactory emf = buildEntityManagerFactory();

	private static EntityManagerFactory buildEntityManagerFactory() {
		String persistenceUnit = System.getProperty("persistence.unit","hsqldb-mem-schema");
		
		try {
			return Persistence.createEntityManagerFactory(persistenceUnit);
		} catch (Exception ex) {
			System.err.println("Initial EntityManagerFactory creation failed:"
					+ ex.getMessage());
			throw new ExceptionInInitializerError(ex);
		}
	}

	public static EntityManagerFactory getEntityManagerFactory() {
		return emf;
	}

	public static EntityManager createEntityManager() {
		return getEntityManagerFactory().createEntityManager();
	}

}
