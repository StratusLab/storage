package eu.stratuslab.storage.disk.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class VolumeChooserStressTest {

	@Before
	// 10 volumes named v1, v2, ..., v10 with each empty
	// VolumeChooser singleton configured with 512 as threshold
	public void setup() {
		List<String> volumeNames = new ArrayList<String>();
		List<Integer> values = new ArrayList<Integer>();
		for (int i = 1; i <= 10; i++) {
			volumeNames.add("v" + i);
			values.add(0);
		}
		VolumeChooserTestHelper.vc(512, volumeNames, values);
	}

	@Test		
	public void checkThatEachRequesterIsServedAndEverythingReleased() throws Exception {

		int nbRequesters = 20;
		Thread[] requesters = new Thread[nbRequesters];
		Collector c = new Collector();
		for (int i = 0; i < nbRequesters; i++) {
			requesters[i] = new Thread(new RequestRelease4Times(c));
			requesters[i].start();
		}
		for (int i = 0; i < nbRequesters; i++) {
			requesters[i].join();
		}

		assertEquals(10, VolumeChooser.getInstance().volumes.size());
		for (Integer value : VolumeChooser.getInstance().volumes.values()) {
			assertEquals(0, (int) value);
		}
		
		// 20 requesters * 4 times * (one req + one rel)
		assertEquals(20*4*2, c.nbSuccess);
		assertEquals(0, c.nbFailures);
	}

	@Test
	public void checkUniformityOfUsage() throws Exception {

		int nbRequesters = 250;
		int nbRequestsPerRequester = 10;
		
		Thread[] requesters = new Thread[nbRequesters];
		Collector c = new Collector();
		for (int i = 0; i < nbRequesters; i++) {
			requesters[i] = new Thread(new RequesterReq(i, nbRequestsPerRequester, c));
			requesters[i].start();
		}
		for (int i = 0; i < nbRequesters; i++) {
			requesters[i].join();
		}

		int idealDistribution = nbRequesters * nbRequestsPerRequester / VolumeChooser.getInstance().volumes.size();		
		for (Integer value : VolumeChooser.getInstance().volumes.values()) {
			assertEquals(idealDistribution, (int)value);	
		}				
		assertEquals(nbRequesters*nbRequestsPerRequester, c.nbSuccess);
		assertEquals(0, c.nbFailures);
	}
	
	@Test
	public void shouldFillUptoMaximumAndRejectAfter() throws Exception {
		
		Map<String, Integer> volumes = VolumeChooser.getInstance().volumes;
		for (Entry<String, Integer> volume: volumes.entrySet()){
			volume.setValue(510);
		}
			
		Assert.assertTrue(VolumeChooser.getInstance().percentageConsumed() > 0.99);	

		Collector collector = new Collector();
		
		int nbRequesters = 10; 
		int nbRequestsPerRequester = 4;		
		
		Thread[] requesters = new Thread[nbRequesters];
		for (int i = 0; i < nbRequesters; i++) {
			requesters[i] = new Thread(new RequesterReq(i, nbRequestsPerRequester, collector));
			requesters[i].start();
		}
		for (int i = 0; i < nbRequesters; i++) {
			requesters[i].join();
		}
		
		// As only 20 slots available, we expect 20 success and 20 "refused" out of the 10*4 requests
		assertEquals(20, collector.nbSuccess);
		assertEquals(20, collector.nbFailures);
	}

	private static class Collector {
		public int nbSuccess;
		public int nbFailures;
		
		public synchronized void success(){
			this.nbSuccess++;
		}
		
		public synchronized void fail(){
			this.nbFailures++;
		}
		
	}
	
	private static abstract class Requester implements Runnable {

		protected Collector collector;

		public Requester(Collector collector) {
			this.collector = collector;
		}
		
		@Override
		public abstract void run();

		protected void doNothing(int min, int offset) {
			try {					
				Thread.sleep(randMillis(min, offset));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		protected long randMillis(int min, int offset) {
			return min + (long) (Math.random() * offset);
		}
	}

	private static class RequesterReq extends Requester {

		private int nbRequests;
		
		public RequesterReq(int id, int nbRequest, Collector collector) {
			super(collector);
			this.nbRequests = nbRequest;			
		}

		@Override
		public void run() {
			for (int i = 0; i < this.nbRequests; i++) {	
				
				doNothing(50, 50);
				
				try{
					String volumeName = VolumeChooser.getInstance().requestVolumeName();
					assertNotNull(volumeName);
					collector.success();
				} catch (IllegalStateException isa) {
					collector.fail();					
				}
			}
		}
	}

	private static class RequestRelease4Times extends Requester {

		public RequestRelease4Times(Collector collector) {
			super(collector);
		}

		@Override
		public void run() {

			for (int i = 0; i < 4; i++) {				
				doNothing(100, 50);
				
				String volumeName = VolumeChooser.getInstance().requestVolumeName();
				assertNotNull(volumeName);
				collector.success();
							
				doNothing(100, 50);
				
				VolumeChooser.getInstance().releaseVolume(volumeName);
				collector.success();
			}
		}
	}
}
