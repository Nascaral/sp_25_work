package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
private PriorityQueue<SleepEntry> sleepers = new PriorityQueue<>();
long now = Machine.timer().getTime();

private static class SleepEntry implements Comparable<SleepEntry> {
    KThread thread;
    long wakeTime;

    SleepEntry(KThread thread, long wakeTime) {
        this.thread = thread;
        this.wakeTime = wakeTime;
    }

    @Override
    public int compareTo(SleepEntry other) {
        return Long.compare(this.wakeTime, other.wakeTime);
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
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}
public boolean cancel(KThread t){
    boolean intStat = Machine.interrupt().disable();
    boolean removed = sleepers.removeIf(e -> e.thread==t);
    Machine.interrupt().restore(intStat);
    return removed;
}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		KThread.currentThread().yield();
		Condition2.handleTimeouts(now);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		while (wakeTime > Machine.timer().getTime())
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
}
