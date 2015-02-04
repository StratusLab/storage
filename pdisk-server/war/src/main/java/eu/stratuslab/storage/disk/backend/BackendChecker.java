package eu.stratuslab.storage.disk.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;
import eu.stratuslab.storage.disk.utils.DiskUtils;

/**
 * Instantiate and call update() to fully initiate the instance, then feed it to
 * a thread runner.
 *
 */
public class BackendChecker {

	private static final Logger logger = Logger.getLogger(BackendChecker.class
			.getName());
	
	private List<VolumeChecker> checkers = new ArrayList<VolumeChecker>();
	
	private BackEndStorage backEndStorage;
	
	private static long CHECKER_STARTUP_SPREAD_DELAY_MILLIS = ServiceConfiguration.getInstance().ISCSI_CHECKER_UPDATE_SLEEP / 2;
	
	public BackendChecker() {
		this(DiskUtils.getDiskStorage());
	}
	
	public BackendChecker(BackEndStorage backEndStorage) {
		this.backEndStorage = backEndStorage;
	}	
	
	public void init() {
		createCheckers();
		checkOnceAndWaitAllEnded();
		checkContinous();
	}

	private void checkOnceAndWaitAllEnded() {
		
		List<Thread> threadCheckers = new ArrayList<Thread>();
		
		for (VolumeChecker checker : checkers) {			
			threadCheckers.add(new Thread(new VolumeCheckerSingleRun(checker)));
		}		
		for (Thread thread : threadCheckers) {
			thread.start();
		}
		for (Thread thread : threadCheckers) {
			try {
				thread.join();
			} catch (InterruptedException e) {			
				e.printStackTrace();
			}
		}
		logger.info("First update done, volumes=" + VolumeChooser.getInstance().volumes);
	}

	private void checkContinous() {		
		int i = 0;		
		for (VolumeChecker checker : checkers) {
			long startupDelay = (CHECKER_STARTUP_SPREAD_DELAY_MILLIS / checkers.size())*i;
			(new Thread(new VolumeCheckerContinuousRun(checker, startupDelay))).start();
			i++;
		}		
	}
	
	private void createCheckers() {
		for (String volume : getBackendProxiesFromConfig()) {
			if (volume != null) {
				volume = volume.trim();

				VolumeChecker checker = new VolumeChecker(backEndStorage, volume);
				checkers.add(checker);
			}
		}		
	}

	protected String[] getBackendProxiesFromConfig() {
		return DiskUtils.getBackendProxiesFromConfig();
	}

}
