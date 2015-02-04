package eu.stratuslab.storage.disk.backend;

public class VolumeCheckerSingleRun implements Runnable {

	private final VolumeChecker checker;

	public VolumeCheckerSingleRun(final VolumeChecker checker) {
		this.checker = checker;
	}
	
	@Override
	public void run() {
		checker.update();		
	}	
	
}
