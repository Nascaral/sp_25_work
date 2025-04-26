package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    private PriorityQueue<SleepEntry> sleepers = new PriorityQueue<>();

    private static class SleepEntry implements Comparable<SleepEntry> {
        final KThread thread;
        final long wakeTime;

        SleepEntry(KThread t, long Time) {
            thread = t;
            wakeTime = Time;
        }

        public int compareTo(SleepEntry other) {
            return Long.compare(wakeTime, other.wakeTime);
        }
    }

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	
	public Alarm() {
		Machine.timer().setInterruptHandler(this::timerInterrupt);
	}



	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
    public void waitUntil (long x) {
	    if ( x <= 0){
		    return; // if time is less than or 0 then no need to sleep
	    }
        long wakeTime = Machine.timer().getTime() + x;

        boolean intStatus = Machine.interrupt().disable();
	    try{
        sleepers.add(new SleepEntry(KThread.currentThread(), wakeTime));
        KThread.sleep();
	    } finally{
        Machine.interrupt().restore(intStatus);
	    }
    }
	
	private void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
			long time = Machine.timer().getTime();
		
		while(!sleepers.isEmpty() && sleepers.peek().wakeTime <= time) {
			KThread thread = sleepers.poll().thread;
			if(thread.status == KThread.statusBlocked) {
			thread.ready();
			}
		}
		Machine.interrupt().restore(intStatus);
		KThread.yield();
	}

	
}
