package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
private LinkedList<KThread> waitQueue = new LinkedList<>();
private static PriorityQueue<TimeEntry> globalTimed = new PriorityQueue<>((a,b)->Long.compare(a.tgt,b.tgt));
long now = Machine.timer().getTime();

/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
  Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean intStat = Machine.interrupt().disable();
    waitQueue.add(KThread.currentThread());
    conditionLock.release();
    KThread.sleep();
    conditionLock.acquire();
    Machine.interrupt().restore(intStat);
	}
private static class TimeEntry {
    KThread t; long tgt;
    TimeEntry(KThread t,long tgt){ this.t=t; this.tgt=tgt; }
}
private PriorityQueue<TimeEntry> timed = new PriorityQueue<>(
    (a,b)->Long.compare(a.tgt,b.tgt));

public void sleepFor(long x) {
    if (x<=0){ sleep(); return; }

    long wake = Machine.timer().getTime()+x;
    boolean intStat = Machine.interrupt().disable();
    timed.add(new TimeEntry(KThread.currentThread(),wake));
    conditionLock.release();
    KThread.sleep();
    conditionLock.acquire();
    Machine.interrupt().restore(intStat);
}
public static void handleTimeouts(long now){
    boolean intStat = Machine.interrupt().disable();
    while (!globalTimed.isEmpty() && globalTimed.peek().tgt<=now)
        globalTimed.poll().t.ready();
    Machine.interrupt().restore(intStat);
}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	
	public void wake() {
	 Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean intStat = Machine.interrupt().disable();
    if (!waitQueue.isEmpty())
        waitQueue.removeFirst().ready();
    Machine.interrupt().restore(intStat);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
	while (true) {
        boolean intStat = Machine.interrupt().disable();
        if (waitQueue.isEmpty()) {
            Machine.interrupt().restore(intStat);
            break;
        }
        waitQueue.removeFirst().ready();
        Machine.interrupt().restore(intStat);
    }
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */

        private Lock conditionLock;
}
