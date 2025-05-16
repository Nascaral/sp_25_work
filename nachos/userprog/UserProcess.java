package nachos.userprog;

import nachos.machine.*;
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
	private Lock exitLock = new Lock();
	private Condition2 exitCondition = new Condition2(exitLock);

	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
		
		// Assign process ID
		pid = nextPID++;
		processes.add(this);
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
		if (!load(name, args))
			return false;

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

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		int bytesCopied = 0;

		while (length > 0) {
			int vpn = vaddr / pageSize;
			int pageOffset = vaddr % pageSize;

			if (vpn < 0 || vpn >= pageTable.length || pageTable[vpn] == null || !pageTable[vpn].valid)
				break;

			int ppn = pageTable[vpn].ppn;
			int paddr = ppn * pageSize + pageOffset;
			int amount = Math.min(length, pageSize - pageOffset);

			System.arraycopy(memory, paddr, data, offset + bytesCopied, amount);
			vaddr += amount;
			bytesCopied += amount;
			length -= amount;
		}
		return bytesCopied;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		int bytesCopied = 0;

		while (length > 0) {
			int vpn = vaddr / pageSize;
			int pageOffset = vaddr % pageSize;

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

    /* ---------- open the executable ---------- */
    OpenFile executable = ThreadedKernel.fileSystem.open(name, /*readOnly=*/false);
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

    /* ---------- count code/data pages ---------- */
    numPages = 0;
    for (int s = 0; s < coff.getNumSections(); s++) {
        CoffSection sec = coff.getSection(s);
        if (sec.getFirstVPN() != numPages) {            // must be contiguous
            coff.close();
            Lib.debug(dbgProcess, "\tfragmented executable");
            return false;
        }
        numPages += sec.getLength();
    }

    /* ---------- make space for stack + arg page ---------- */
    final int stackVPN   = numPages;            // first stack page
    final int argVPN     = numPages + stackPages;   // single arg page
    int totalPages       = numPages + stackPages + 1;

    pageTable = new TranslationEntry[totalPages];

    /* ---------- allocate every page up-front ---------- */
    for (int vpn = 0; vpn < totalPages; vpn++) {
        int ppn = UserKernel.allocatePage();
        if (ppn == -1) {                         // out of memory → roll-back
            for (int i = 0; i < vpn; i++)
                UserKernel.freePage(pageTable[i].ppn);
            coff.close();
            return false;
        }
        boolean readOnly = false;               // will be patched for code below
        pageTable[vpn] = new TranslationEntry(vpn, ppn, true, readOnly,false,false);
    }

    /* ---------- mark text / rodata pages read-only & copy them ---------- */
    for (int s = 0; s < coff.getNumSections(); s++) {
        CoffSection sec = coff.getSection(s);
        for (int i = 0; i < sec.getLength(); i++) {
            int vpn = sec.getFirstVPN() + i;
            pageTable[vpn].readOnly = sec.isReadOnly();
            sec.loadPage(i, pageTable[vpn].ppn);
        }
    }

    /* ---------- build the argument page ---------- */
    /* pack: [argv[] table][strings...] inside the very last page            */
    int argStringsSize = 0;
    for (String a : args) argStringsSize += a.length() + 1;   // +1 for '\0'
    int argvTableSize  = 4 * (args.length + 1);               // +NULL entry
    if (argvTableSize + argStringsSize > pageSize) {          // won’t fit
        coff.close();
        return false;
    }

    initialPC = coff.getEntryPoint();
    initialSP = stackVPN * pageSize + stackPages * pageSize;  // very top of stack
    argc      = args.length;
    argv      = argVPN * pageSize;                            // start of table

    int       entry      = argv;                              // table pointer
    int       strOffset  = argv + argvTableSize;              // first string

    for (String a : args) {
        /* write argv[i] */
        Lib.bytesFromInt(wordBuf, 0, strOffset);
        Lib.assertTrue(writeVirtualMemory(entry, wordBuf) == 4);
        entry += 4;

        /* write actual string + NUL */
        byte[] asBytes = (a + '\0').getBytes();
        Lib.assertTrue(writeVirtualMemory(strOffset, asBytes) == asBytes.length);
        strOffset += asBytes.length;
    }
    /* argv[argc] = NULL */
    Lib.bytesFromInt(wordBuf, 0, 0);
    Lib.assertTrue(writeVirtualMemory(entry, wordBuf) == 4);

    return true;
}

/* small scratch buffer to avoid reallocating every int */
private static final byte[] wordBuf = new byte[4];


	/**
	 * Release any resources allocated by <tt>load()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++) {
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
				
				// Allocate a physical page
				int ppn = UserKernel.allocatePage();
				if (ppn == -1) {
					// If we can't allocate a page, free all previously allocated pages
					for (int j = 0; j < vpn; j++) {
						if (pageTable[j] != null && pageTable[j].valid) {
							UserKernel.freePage(pageTable[j].ppn);
						}
					}
					return false;
				}
				
				// Create page table entry
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, isReadOnly, false, false);
				
				// Load the page
				section.loadPage(i, ppn);
			}
		}
		return true;
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything is 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
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
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		
		// Store exit status
		exitLock.acquire();
		exitStatus = status;
		hasExited = true;
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
		
		
		// If this is the last process, terminate the kernel
		if (processes.isEmpty())
			Kernel.kernel.terminate();
		
		// Finish the thread
		thread.finish();
		
		return 0;
	}

	/**
	 * Handle the create() system call.
	 */
	private int handleCreate(int nameAddr) {
		String fileName = readVirtualMemoryString(nameAddr, 256);
		if (fileName == null)
			return -1;

		OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
		if (file == null)
			return -1;

		int fd = nextFileDescriptor++;
		if (fd >= fileTable.length) {
			file.close();
			return -1;
		}

		fileTable[fd] = file;
		return fd;
	}

	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int nameAddr) {
		String fileName = readVirtualMemoryString(nameAddr, 256);
		if (fileName == null)
			return -1;

		OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
		if (file == null)
			return -1;

		int fd = nextFileDescriptor++;
		if (fd >= fileTable.length) {
			file.close();
			return -1;
		}

		fileTable[fd] = file;
		return fd;
	}

	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int fd, int bufferAddr, int size) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null)
			return -1;

		if (size < 0)
			return -1;

		byte[] buffer = new byte[size];
		int bytesRead = fileTable[fd].read(buffer, 0, size);
		if (bytesRead < 0)
			return -1;

		int bytesWritten = writeVirtualMemory(bufferAddr, buffer, 0, bytesRead);
		if (bytesWritten != bytesRead)
			return -1;

		return bytesRead;
	}

	/**
	 * Handle the write() system call.
	 */
	private int handleWrite(int fd, int bufferAddr, int size) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null)
			return -1;

		if (size < 0)
			return -1;

		byte[] buffer = new byte[size];
		int bytesRead = readVirtualMemory(bufferAddr, buffer, 0, size);
		if (bytesRead != size)
			return -1;

		int bytesWritten = fileTable[fd].write(buffer, 0, size);
		if (bytesWritten != size)
			return -1;

		return bytesWritten;
	}

	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fd) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null)
			return -1;

		fileTable[fd].close();
		fileTable[fd] = null;
		return 0;
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int nameAddr) {
		String fileName = readVirtualMemoryString(nameAddr, 256);
		if (fileName == null)
			return -1;

		return ThreadedKernel.fileSystem.remove(fileName) ? 0 : -1;
	}

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
		
		if (!child.execute(name, args))
			return -1;

		return child.getPID();
	}

	private int handleJoin(int pid, int statusAddr) {
    // 1. locate the child among *all* processes
    UserProcess child = null;
    for (UserProcess p : processes)
        if (p.pid == pid && p.parent == this) { child = p; break; }

    if (child == null) return -1;      // not a child of this process

    /* 2. wait until that child sets hasExited = true */
    child.exitLock.acquire();
    while (!child.hasExited) child.exitCondition.sleep();

    /* 3. give status back to parent (if pointer supplied) */
    if (statusAddr != 0) {
        byte[] bytes = Lib.bytesFromInt(child.exitStatus);
        if (writeVirtualMemory(statusAddr, bytes) != 4) {
            child.exitLock.release();
            return -1;
        }
    }
    child.exitLock.release();

    /* 4. now it is safe to forget the child object */
    processes.remove(child);

    /* 5. if every remaining process in the system has exited,
          shut the machine down */
    boolean everyoneGone = true;
    for (UserProcess p : processes)
        if (!p.hasExited) { everyoneGone = false; break; }
    if (everyoneGone) Kernel.kernel.terminate();

    return 1;                          // join succeeded
}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * </tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * </tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * </tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a2);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
	 * The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 */
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

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
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
