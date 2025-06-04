import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;


/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
        private OpenFile[] fileTable = new OpenFile[16];
        private int nextFileDescriptor = 2; // 0 and 1 are reserved for console

	// Process ID management
	private static int nextPID = 0;
	private int pid;
	private UserProcess parent;
        private static List<UserProcess> processes = new ArrayList<>();
	private int exitStatus;
        private boolean hasExited = false;
        private boolean exitedNormally = false;
	private Lock exitLock = new Lock();
	private Condition2 exitCondition = new Condition2(exitLock);
	private List<UserProcess> childProcesses = new ArrayList<>();

        public UserProcess() {
                pageTable = new TranslationEntry[0]; // Will be resized in load()
                fileTable[0] = UserKernel.console.openForReading();
                fileTable[1] = UserKernel.console.openForWriting();
		
		// Assign process ID
		pid = nextPID++;
                processes.add(this);
                exitedNormally = false;
       }

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
       public boolean execute(String name, String[] args) {
                if (!load(name, args)) {
                        processes.remove(this);
                        unloadSections();
                        return false;
                }

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	public class UserProcess {
			if (vpn < 0 || vpn >= pageTable.length || pageTable[vpn] == null || !pageTable[vpn].valid || pageTable[vpn].readOnly)
				break;

			int ppn = pageTable[vpn].ppn;
			int paddr = ppn * pageSize + pageOffset;
			int amount = Math.min(length, pageSize - pageOffset);

			System.arraycopy(data, offset + bytesCopied, memory, paddr, amount);
			vaddr += amount;
			bytesCopied += amount;
			length -= amount;
		}
		return bytesCopied;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
        private boolean load(String name, String[] args) {
                Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

                OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
                if (executable == null) {
                        Lib.debug(dbgProcess, "\topen failed");
                        return false;
                }

                try {
                        coff = new Coff(executable);
                } catch (EOFException e) {
                        executable.close();
                        Lib.debug(dbgProcess, "\tcoff load failed");
                        return false;
                }

                numPages = 0;
                for (int s = 0; s < coff.getNumSections(); s++) {
                        CoffSection sec = coff.getSection(s);
                        if (sec.getFirstVPN() != numPages) {
                                coff.close();
                                Lib.debug(dbgProcess, "\tfragmented executable");
                                return false;
                        }
                        numPages += sec.getLength();
                }

                byte[][] argvStrings = new byte[args.length][];
                int argsSize = 0;
                for (int i = 0; i < args.length; i++) {
                        argvStrings[i] = args[i].getBytes();
                        argsSize += 4 + argvStrings[i].length + 1;
                }
                if (argsSize > pageSize) {
                        coff.close();
                        Lib.debug(dbgProcess, "\targuments too long");
                        return false;
                }

                int stackBase = numPages;
                numPages += stackPages;
                initialSP = numPages * pageSize;
                numPages++;

                pageTable = new TranslationEntry[numPages];
                int allocated = 0;
                for (; allocated < numPages; allocated++) {
                        int ppn = UserKernel.allocatePage();
                        if (ppn == -1) {
                                for (int i = 0; i < allocated; i++)
                                        UserKernel.freePage(pageTable[i].ppn);
                                coff.close();
                                return false;
                        }
                        pageTable[allocated] = new TranslationEntry(allocated, ppn, true, false, false, false);
                }

                if (!loadSections()) {
                        for (int i = 0; i < numPages; i++)
                                UserKernel.freePage(pageTable[i].ppn);
                        coff.close();
                        return false;
                }

                int entryOffset = (numPages - 1) * pageSize;
                int stringOffset = entryOffset + args.length * 4;

                argc = args.length;
                argv = entryOffset;

                for (int i = 0; i < args.length; i++) {
                        Lib.assertTrue(writeVirtualMemory(entryOffset, Lib.bytesFromInt(stringOffset)) == 4);
                        entryOffset += 4;
                        byte[] arg = argvStrings[i];
                        Lib.assertTrue(writeVirtualMemory(stringOffset, arg) == arg.length);
                        stringOffset += arg.length;
                        Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
                        stringOffset += 1;
                }
                Lib.assertTrue(writeVirtualMemory(entryOffset, Lib.bytesFromInt(0)) == 4);

                initialPC = coff.getEntryPoint();

                return true;
        }

	/**
	 * Release any resources allocated by <tt>load()</tt>.
	 */
        protected void unloadSections() {
                if (pageTable == null)
                        return;
                for (int i = 0; i < numPages; i++) {
                        if (pageTable[i] != null)
                                UserKernel.freePage(pageTable[i].ppn);
                }
	}

	/**
	 * Load sections from the executable into memory.
	 * This is the base implementation that loads all pages immediately.
	 * VMProcess overrides this to implement demand paging.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			boolean isReadOnly = section.isReadOnly();

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				// Update page table entry to be read-only if needed
				pageTable[vpn].readOnly = isReadOnly;
				
				// Load the page
				section.loadPage(i, pageTable[vpn].ppn);
@@ -393,62 +390,67 @@ public class UserProcess {
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		// Only root process (PID 0) can halt
		if (pid != 0)
			return -1;
			
		Lib.debug(dbgProcess, "UserProcess.handleHalt");
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
        private int handleExit(int status) {
                return handleExit(status, true);
        }

        private int handleExit(int status, boolean normal) {
                // Do not remove this call to the autoGrader...
                Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		
		// Store exit status
		exitLock.acquire();
                exitStatus = status;
                hasExited = true;
                exitedNormally = normal;
		exitCondition.wake();
		exitLock.release();
		
		// Close all open files
		for (int i = 0; i < fileTable.length; i++) {
			if (fileTable[i] != null) {
				fileTable[i].close();
				fileTable[i] = null;
			}
		}
		
		// Free all allocated pages
		unloadSections();
		
		// Close the executable
		if (coff != null) {
			coff.close();
			coff = null;
		}
		
		// Remove from process list
		processes.remove(this);
		
		// If this is the last process, terminate the kernel
		if (processes.isEmpty())
@@ -592,117 +594,111 @@ public class UserProcess {
	private int handleExec(int nameAddr, int argc, int argvAddr) {
		// Read the executable name
		String name = readVirtualMemoryString(nameAddr, 256);
		if (name == null || !name.endsWith(".coff"))
			return -1;
		
		// Read the arguments
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			// Read argument pointer
			byte[] ptrBytes = new byte[4];
			if (readVirtualMemory(argvAddr + i * 4, ptrBytes) != 4)
				return -1;
			int argAddr = Lib.bytesToInt(ptrBytes, 0);
			
			// Read argument string
			args[i] = readVirtualMemoryString(argAddr, 256);
			if (args[i] == null)
				return -1;
		}
		
		// Create new process
		UserProcess child = newUserProcess();
		child.setParent(this);

                this.childProcesses.add(child);

                if (!child.execute(name, args)) {
                        childProcesses.remove(child);
                        return -1;
                }

                return child.getPID();
        }


private int handleJoin(int pid, int statusAddr) {
    // Debug output
    Lib.debug(dbgProcess, "handleJoin called with pid=" + pid + ", statusAddr=" + statusAddr);
    
    // Find the child process
    UserProcess child = null;
    for (UserProcess p : this.childProcesses) {
        if (p.getPID() == pid) {
            child = p;
            break;
        }
    }

    // If not a child of this process, return -1
    if (child == null) {
        Lib.debug(dbgProcess, "handleJoin: PID " + pid + " is not a child of this process");
        return -1;
    }
    
    Lib.debug(dbgProcess, "handleJoin: Found child process with PID " + pid);

    // Wait for the child to exit
    child.exitLock.acquire();
    while (!child.hasExited) {
        Lib.debug(dbgProcess, "handleJoin: Waiting for child " + pid + " to exit");
        child.exitCondition.sleep();
    }
    
    int childExitStatus = child.exitStatus;
    Lib.debug(dbgProcess, "handleJoin: Child " + pid + " exited with status " + childExitStatus);
    child.exitLock.release();

    // Check if statusAddr is 0 (NULL pointer check)
    if (statusAddr == 0) {
        Lib.debug(dbgProcess, "handleJoin: statusAddr is NULL, ignoring status write");
    } else {
        byte[] statusBytes = Lib.bytesFromInt(childExitStatus);
        Lib.debug(dbgProcess, "handleJoin: Writing status " + childExitStatus + " to address " + statusAddr);

        if (writeVirtualMemory(statusAddr, statusBytes) != 4) {
            Lib.debug(dbgProcess, "handleJoin: Write status failed - address might be invalid");
            childProcesses.remove(child);
            return 0;
        }
    }

    childProcesses.remove(child);

    Lib.debug(dbgProcess, "handleJoin: Successfully joined with child " + pid);
    return child.exitedNormally ? 1 : 0;
}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;


public void handleException(int cause) {
    Processor processor = Machine.processor();

    switch (cause) {
    case Processor.exceptionSyscall:
        int result = handleSyscall(processor.readRegister(Processor.regV0),
                processor.readRegister(Processor.regA0),
                processor.readRegister(Processor.regA1),
                processor.readRegister(Processor.regA2),
                processor.readRegister(Processor.regA3));
        processor.writeRegister(Processor.regV0, result);
        processor.advancePC();
        break;

    case Processor.exceptionAddressError:
        Lib.debug(dbgProcess, "Address error exception");
        handleExit(-1, false); // abnormal exit
        break;

    case Processor.exceptionBusError:
        Lib.debug(dbgProcess, "Bus error exception");
        handleExit(-1, false);
        break;

    case Processor.exceptionIllegalInstruction:
        Lib.debug(dbgProcess, "Illegal instruction exception");
        handleExit(-1, false);
        break;

    case Processor.exceptionOverflow:
        Lib.debug(dbgProcess, "Overflow exception");
        handleExit(-1, false);
        break;

    case Processor.exceptionPageFault:
        Lib.debug(dbgProcess, "Page fault exception");
        handleExit(-1, false);
        break;

    case Processor.exceptionReadOnly:
        Lib.debug(dbgProcess, "Read-only exception");
        handleExit(-1, false);
        break;

    default:
        Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
        handleExit(-1, false);
        break;
    }
}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	/** The program counter and stack pointer. */
	private int initialPC, initialSP;

	/** The arguments to the program. */
	private int argc, argv;




	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	public int getPID() {
		return pid;
	}

	public void setParent(UserProcess parent) {
		this.parent = parent;
	}

	public UserProcess getParent() {
		return parent;
	}
}
