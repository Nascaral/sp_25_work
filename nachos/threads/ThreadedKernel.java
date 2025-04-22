package nachos.threads;

import nachos.machine.*;

/**
 * A multi-threaded OS kernel.
 */
public class ThreadedKernel extends Kernel {
	/**
	 * Allocate a new multi-threaded kernel.
	 */
	public ThreadedKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a scheduler, the first thread, and an
	 * alarm, and enables interrupts. Creates a file system if necessary.
	 */
	public void initialize(String[] args) {
		// set scheduler
		String schedulerName = Config.getString("ThreadedKernel.scheduler");
		scheduler = (Scheduler) Lib.constructObject(schedulerName);

		// set fileSystem
		String fileSystemName = Config.getString("ThreadedKernel.fileSystem");
		if (fileSystemName != null)
			fileSystem = (FileSystem) Lib.constructObject(fileSystemName);
		else if (Machine.stubFileSystem() != null)
			fileSystem = Machine.stubFileSystem();
		else
			fileSystem = null;

		// start threading
		new KThread(null);

		alarm = new Alarm();

		Machine.interrupt().enable();
	}

	/**
	 * Test this kernel. Test the <tt>KThread</tt>, <tt>Semaphore</tt>,
	 * <tt>SynchList</tt>, and <tt>ElevatorBank</tt> classes. Note that the
	 * autograder never calls this method, so it is safe to put additional tests
	 * here.
	 */
	public void selfTest() {
		KThread.selfTest();
		Semaphore.selfTest();
		SynchList.selfTest();
		if (Machine.bank() != null) {
			ElevatorBank.selfTest();
	

		}

		new KThread(() -> {
    long start = Machine.timer().getTime();
    System.out.println("sleep 500 ticks…");
    ThreadedKernel.alarm.waitUntil(500);
    System.out.println("slept → "+(Machine.timer().getTime()-start)+" ticks");
}).setName("AlarmTest").fork();

KThread child = new KThread(() -> {
    System.out.println("child running");
    KThread.yield();
    System.out.println("child done");
}).setName("C");

KThread parent = new KThread(() -> {
    System.out.println("parent waiting");
    child.join();
    System.out.println("parent resumes");
}).setName("P");

child.fork(); parent.fork();
Lock l = new Lock();
Condition2 cv = new Condition2(l);
final int[] counter = {0};

KThread waiter = new KThread(() -> {
    l.acquire();
    while (counter[0]==0) cv.sleep();  // should block
    System.out.println("waiter sees counter="+counter[0]);
    l.release();
}).setName("Waiter");

KThread signaler = new KThread(() -> {
    l.acquire();
    counter[0]=42;
    cv.wake();                         // release waiter
    l.release();
}).setName("Signaler");

waiter.fork(); signaler.fork();
Rendezvous rv = new Rendezvous();
KThread A = new KThread(() -> {
    int got = (int) rv.exchange(1, 111);
    System.out.println("A got "+got);
});
KThread B = new KThread(() -> {
    int got = (int) rv.exchange(1, 222);
    System.out.println("B got "+got);
});
A.fork(); B.fork();





	}

	/**
	 * A threaded kernel does not run user programs, so this method does
	 * nothing.
	 */
	public void run() {
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		Machine.halt();
	}

	/** Globally accessible reference to the scheduler. */
	public static Scheduler scheduler = null;

	/** Globally accessible reference to the alarm. */
	public static Alarm alarm = null;

	/** Globally accessible reference to the file system. */
	public static FileSystem fileSystem = null;

	// dummy variables to make javac smarter
	private static RoundRobinScheduler dummy1 = null;

	private static PriorityScheduler dummy2 = null;

	private static LotteryScheduler dummy3 = null;

	private static Condition2 dummy4 = null;

        //private static Communicator dummy5 = null;

	private static Rider dummy6 = null;

	private static ElevatorController dummy7 = null;

        private static GameMatch dummy8 = null;

        private static Future dummy9 = null;
}
