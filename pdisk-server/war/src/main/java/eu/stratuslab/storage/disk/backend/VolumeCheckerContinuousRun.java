package eu.stratuslab.storage.disk.backend;

import java.util.logging.Logger;

import eu.stratuslab.storage.disk.main.ServiceConfiguration;

public class VolumeCheckerContinuousRun implements Runnable {
	
	private static final Logger logger = Logger.getLogger(VolumeCheckerContinuousRun.class
					.getName());
	
	private final VolumeChecker checker;

	private final long startupDelay;
	private final static long UPDATE_SLEEP = ServiceConfiguration.getInstance().ISCSI_CHECKER_UPDATE_SLEEP;

	public VolumeCheckerContinuousRun(final VolumeChecker checker, final long startupDelay) {
		this.checker = checker;
		this.startupDelay = startupDelay;
	}

	@Override
	public void run() {
		
		initialDelay();
		
		while (true) {
			sleep();
			checker.update();
		}
	}

	private void initialDelay() {
		try {			
			logger.info("Will sleep for " + startupDelay + " before starting continuous updates");			
			Thread.sleep(startupDelay);			
		} catch (InterruptedException e) {		
			e.printStackTrace();
		}
	}

	private void sleep() {
		try {			
			logger.info("Sleeping " + UPDATE_SLEEP / 1000 + " sec.");
			Thread.sleep(UPDATE_SLEEP);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
