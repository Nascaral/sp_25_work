package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});

		// Initialize physical memory management
		int numPhysPages = Machine.processor().getNumPhysPages();
		freePages = new boolean[numPhysPages];
		for (int i = 0; i < numPhysPages; i++) {
			freePages[i] = true;
		}
		pageLock = new Lock();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		/* Skip the console test by default to avoid having to
		 * type 'q' when running Nachos.  To use the test,
		 * just remove the return. */
		if (true)
		    return;

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		if (!process.execute(shellProgram, new String[] {})) {
		    System.out.println ("UserProcess.execute failed trying to open and load executable '" +
					shellProgram + "',\ntrying '" +
					shellProgram + ".coff' instead.");
		    shellProgram += ".coff";
		    if (!process.execute(shellProgram, new String[] {})) {
			System.out.println ("Also failed on '" +
					    shellProgram + "', aborting.");
			Lib.assertTrue(false);
		    }

		}

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/**
	 * Allocate a physical page.
	 * 
	 * @return the physical page number, or -1 if no pages are available.
	 */
public static int allocatePage() {
    pageLock.acquire();
    int page = -1;
    // Reverse allocation to test non-contiguous page assignment
    for (int i = freePages.length - 1; i >= 0; i--) {
        if (freePages[i]) {
            freePages[i] = false;
            page = i;
            break;
        }
    }
    pageLock.release();
    return page;
}

	/**
	 * Free a physical page.
	 * 
	 * @param page the physical page number to free.
	 */
	public static void freePage(int page) {
		pageLock.acquire();
		freePages[page] = true;
		pageLock.release();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	/** Physical memory management */
	private static boolean[] freePages;
	private static Lock pageLock;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
}
