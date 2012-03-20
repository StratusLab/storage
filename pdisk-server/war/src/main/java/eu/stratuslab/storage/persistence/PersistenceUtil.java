package eu.stratuslab.storage.persistence;

import java.util.logging.Logger;

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
			Logger.getLogger("org.restlet").severe("Initial EntityManagerFactory creation failed:"
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
