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
        long wakeTime = Machine.timer().getTime() + x;

        boolean intStatus = Machine.interrupt().disable();
        sleepers.add(new SleepEntry(KThread.currentThread(), wakeTime));   // FIX: param order
        KThread.currentThread().sleep();
        Machine.interrupt().restore(intStatus);
    }
	
	private void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
			long time = Machine.timer().getTime();
		
		while(!sleepers.isEmpty() && sleepers.peek().wakeTime <= time) {
			sleepers.poll().thread.ready();
			
		}
		Machine.interrupt().restore(intStatus);
		KThread.yield();
	}

	
        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */

