package gr.ntua.ivml.mint.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import gr.ntua.ivml.mint.db.DB;


// all the Subjobs in submitted jobs are serially done in one thread
// and they take turns in round robin fashion
public class JobScheduler {
	
	private static final Logger log = Logger.getLogger( JobScheduler.class);
	BlockingDeque<Job> jobQueue = new LinkedBlockingDeque<Job>();

	ConcurrentHashMap<Job,AtomicInteger> runningJobs = new ConcurrentHashMap<>();
	List<Thread> workers = new ArrayList<>();
	
	public static JobScheduler create( int workerCount ) {
		JobScheduler js = new JobScheduler();
		
		if( workerCount < 2 ) {
			Runnable worker = js.new SingleWorker();
			Thread t = new Thread(worker);
			js.workers.add( t );
			t.start();
		} else {
			for( int i=0; i< workerCount; i++ ) {
				Runnable worker = js.new MultiWorker();
				Thread t  = new Thread(worker);
				js.workers.add( t );
				t.start();
			}
		}
		return js;
	}
	
	
	public interface Job {
		
		// true if the next call to executeNextSubjob will find work
		public boolean hasMoreJobs();
		
		// for Job Scheduler with more than one worker, this needs to be thread safe
		// execute the next job if there is one. 
		public void executeNextSubjob(); 

		// this is only called once and only when all executeNextSubjob returned
		public void finished();
	}
		
	public void addJob( Job newJob ) {
		runningJobs.put( newJob, new AtomicInteger(0));
		jobQueue.add( newJob );
	}
	
	public void shutdown() {
		for (Thread thread : workers) {
		      thread.interrupt(); // Interrupt the thread gracefully
		      try {
		        thread.join(); // Wait for the thread to finish
		      } catch (InterruptedException e) {
		        // Handle interruption exception if needed
		      }
		}	    
	    workers.clear();
	}
	
	
	public class SingleWorker implements Runnable {
		public void run() {
			DB.getSession().beginTransaction();

			while( true ) {
				Job currentJob = null;
				try {
					currentJob = jobQueue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					log.error( "Interrupted",e  );
					break;
				}
	
				// if there is no other job, no shuffling is necessary
				do {
					if( !currentJob.hasMoreJobs()) {
						break;
					} else {
						try {
							log.debug( "Execute Next SubJob");
							currentJob.executeNextSubjob();
						} catch( Throwable th ) {
							log.error( "Subjob execute exception");
						}
					}
				} while( jobQueue.isEmpty());
				
				if( !currentJob.hasMoreJobs()) { 
					try {
						log.debug( "Finish Job" );
						currentJob.finished();
					} catch( Throwable th ) {
						log.error( "Subjob finish exception");
					}
				} else 
					jobQueue.add( currentJob );
			}
		}
	}
	
	public class MultiWorker implements Runnable {
		public void run() {
			DB.getSession().beginTransaction();

			while( true ) {
				Job currentJob = null;
				try {
					currentJob = jobQueue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					log.error( "Interrupted",e  );
					break;
				}
				
				// someone else should have the last task of this job
				if( !currentJob.hasMoreJobs()) 
					continue;
				

				AtomicInteger runningCounter = runningJobs.get(currentJob );
				// someone called finished on this job
				if( runningCounter == null ) continue;
				runningCounter.incrementAndGet();
				
				jobQueue.add( currentJob );
				
				log.debug( "Execute Next SubJob");
				currentJob.executeNextSubjob();

				int numOfWorkers = runningCounter.decrementAndGet();

				// theoretically multiple workers could come here
				if(  !currentJob.hasMoreJobs() && numOfWorkers == 0 ) {
					numOfWorkers = runningCounter.incrementAndGet();
					// but only one can be the first here
					if( numOfWorkers == 1 ) {
						log.debug( "Finish Job" );
						currentJob.finished();
						runningJobs.remove(currentJob);
						continue;
					}
				}
			}
		}
	}
}
