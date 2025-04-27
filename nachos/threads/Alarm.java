package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    private static class SleepEntry implements Comparable<SleepEntry> {
        final KThread thread;
        final long wakeTime;

        SleepEntry(KThread t, long w) {
            thread = t;
            wakeTime = w;
        }

       public int compareTo(SleepEntry o) {
            return (wakeTime < o.wakeTime) ? -1 :
                   (wakeTime == o.wakeTime ? 0 : 1);
        }
    }

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	 private final PriorityQueue<SleepEntry> sleepers = new PriorityQueue<>();
	public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
            public void run() { timerInterrupt(); }
		   });
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
        long wake = Machine.timer().getTime() + x;

        boolean old = Machine.interrupt().disable();
        sleepers.add(new SleepEntry(KThread.currentThread(), wake));
        KThread.sleep();
        Machine.interrupt().restore(old);
    }
	
	private void timerInterrupt() {
		boolean old = Machine.interrupt().disable();
			 try {
			long now = Machine.timer().getTime();   // fresh each pass
           		 while (!sleepers.isEmpty()) {
        	if (sleepers.peek().wakeTime > now) break;
                sleepers.poll().thread.ready();
            }
				 Condition2.handleTimeouts(now);
        } finally {
            Machine.interrupt().restore(old);
        }
        KThread.yield();   // let a just-readied thread run
	}

	
}
