package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

public class UserProcess {
	private OpenFile[] fileTable = new OpenFile[16];
	private int nextFileDescriptor = 2;

	private static int nextPID = 0;
	private int pid;
	private UserProcess parent;
	private static List<UserProcess> processes = new ArrayList<>();
	private int exitStatus;
	private boolean hasExited = false;
	private Lock exitLock = new Lock();
	private Condition2 exitCondition = new Condition2(exitLock);
	private List<UserProcess> childProcesses = new ArrayList<>();
	
	private static HashMap<String, Pipe> pipes = new HashMap<>();
	private static Lock pipeLock = new Lock();

	public UserProcess() {
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
		
		pid = nextPID++;
		processes.add(this);
	}

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

	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	public void saveState() {
	}

	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

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

	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

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

	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

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

	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argsSize += args[i].length() + 1;
		}
		argsSize += (args.length + 1) * 4;

		int argPages = (argsSize + pageSize - 1) / pageSize;
		if (argPages > 1) {
			coff.close();
			return false;
		}

		numPages += stackPages;
		numPages += 1;

		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			int ppn = UserKernel.allocatePage();
			if (ppn == -1) {
				for (int j = 0; j < i; j++) {
					UserKernel.freePage(pageTable[j].ppn);
				}
				coff.close();
				Lib.debug(dbgProcess, "\tinsufficient physical memory");
				return false;
			}
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		}

		if (!loadSections()) {
			unloadSections();
			coff.close();
			Lib.debug(dbgProcess, "\tloadSections failed");
			return false;
		}

		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		for (int i = 0; i < args.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, args[i].getBytes()) == args[i].length());
			stringOffset += args[i].length();
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		initialPC = coff.getEntryPoint();
		initialSP = numPages * pageSize;
		argc = args.length;
		argv = (numPages - 1) * pageSize;

		return true;
	}

	protected void unloadSections() {
		for (int i = 0; i < pageTable.length; i++) {
			if (pageTable[i] != null) {
				UserKernel.freePage(pageTable[i].ppn);
			}
		}
	}

	protected boolean loadSections() {
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			boolean isReadOnly = section.isReadOnly();

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				pageTable[vpn].readOnly = isReadOnly;
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}
		return true;
	}

	public void initRegisters() {
		Processor processor = Machine.processor();

		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	private int handleHalt() {
		if (pid != 0)
			return -1;
			
		Lib.debug(dbgProcess, "UserProcess.handleHalt");
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExit(int status) {
		Machine.autoGrader().finishingCurrentProcess(status);

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		
		exitLock.acquire();
		exitStatus = status;
		hasExited = true;
		exitCondition.wakeAll();
		exitLock.release();
		
		for (int i = 0; i < fileTable.length; i++) {
			if (fileTable[i] != null) {
				fileTable[i].close();
				fileTable[i] = null;
			}
		}
		
		unloadSections();
		
		if (coff != null) {
			coff.close();
			coff = null;
		}
		
		processes.remove(this);
		
		if (processes.isEmpty())
			Kernel.kernel.terminate();
		
		thread.finish();
		
		return 0;
	}

	private int handleCreate(int nameAddr) {
		String fileName = readVirtualMemoryString(nameAddr, 256);
		if (fileName == null)
			return -1;

		if (fileName.startsWith("/pipe/")) {
			return handlePipeCreate(fileName);
		}

		OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
		if (file == null)
			return -1;

		for (int fd = 2; fd < fileTable.length; fd++) {
			if (fileTable[fd] == null) {
				fileTable[fd] = file;
				return fd;
			}
		}

		file.close();
		return -1;
	}

	private int handlePipeCreate(String pipeName) {
		pipeLock.acquire();
		
		if (pipes.containsKey(pipeName)) {
			pipeLock.release();
			return -1;
		}
		
		Pipe pipe = new Pipe();
		pipes.put(pipeName, pipe);
		
		PipeFile pipeFile = new PipeFile(pipe, true);
		
		for (int fd = 2; fd < fileTable.length; fd++) {
			if (fileTable[fd] == null) {
				fileTable[fd] = pipeFile;
				pipeLock.release();
				return fd;
			}
		}
		
		pipes.remove(pipeName);
		pipeLock.release();
		return -1;
	}

	private int handleOpen(int nameAddr) {
		String fileName = readVirtualMemoryString(nameAddr, 256);
		if (fileName == null)
			return -1;

		if (fileName.startsWith("/pipe/")) {
			return handlePipeOpen(fileName);
		}

		OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
		if (file == null)
			return -1;

		for (int fd = 2; fd < fileTable.length; fd++) {
			if (fileTable[fd] == null) {
				fileTable[fd] = file;
				return fd;
			}
		}

		file.close();
		return -1;
	}

	private int handlePipeOpen(String pipeName) {
		pipeLock.acquire();
		
		Pipe pipe = pipes.get(pipeName);
		if (pipe == null) {
			pipeLock.release();
			return -1;
		}
		
		PipeFile pipeFile = new PipeFile(pipe, false);
		
		for (int fd = 2; fd < fileTable.length; fd++) {
			if (fileTable[fd] == null) {
				fileTable[fd] = pipeFile;
				pipeLock.release();
				return fd;
			}
		}
		
		pipeLock.release();
		return -1;
	}

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
		return bytesWritten;
	}

	private int handleClose(int fd) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null)
			return -1;

		fileTable[fd].close();
		fileTable[fd] = null;
		return 0;
	}

	private int handleUnlink(int nameAddr) {
		String fileName = readVirtualMemoryString(nameAddr, 256);
		if (fileName == null)
			return -1;

		if (fileName.startsWith("/pipe/")) {
			pipeLock.acquire();
			pipes.remove(fileName);
			pipeLock.release();
			return 0;
		}

		return ThreadedKernel.fileSystem.remove(fileName) ? 0 : -1;
	}

	private int handleExec(int nameAddr, int argc, int argvAddr) {
		String name = readVirtualMemoryString(nameAddr, 256);
		if (name == null || !name.endsWith(".coff"))
			return -1;
		
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] ptrBytes = new byte[4];
			if (readVirtualMemory(argvAddr + i * 4, ptrBytes) != 4)
				return -1;
			int argAddr = Lib.bytesToInt(ptrBytes, 0);
			
			args[i] = readVirtualMemoryString(argAddr, 256);
			if (args[i] == null)
				return -1;
		}
		
		UserProcess child = newUserProcess();
		child.setParent(this);

		this.childProcesses.add(child);
		
		if (!child.execute(name, args))
			return -1;

		return child.getPID();
	}

	private int handleJoin(int pid, int statusAddr) {
		UserProcess child = null;
		for (UserProcess p : this.childProcesses) {
			if (p.getPID() == pid) {
				child = p;
				break;
			}
		}

		if (child == null) {
			return -1;
		}

		child.exitLock.acquire();
		while (!child.hasExited) {
			child.exitCondition.sleep();
		}
		
		int childExitStatus = child.exitStatus;
		child.exitLock.release();

		if (statusAddr != 0) {
			byte[] statusBytes = Lib.bytesFromInt(childExitStatus);
			if (writeVirtualMemory(statusAddr, statusBytes) != 4) {
				childProcesses.remove(child);
				return 0;
			}
		}

		childProcesses.remove(child);
		
		if (childExitStatus == -1) {
			return 0;
		}
		
		return 1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
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
			handleExit(-1);
			break;

		case Processor.exceptionBusError:
			Lib.debug(dbgProcess, "Bus error exception");
			handleExit(-1);
			break;

		case Processor.exceptionIllegalInstruction:
			Lib.debug(dbgProcess, "Illegal instruction exception");
			handleExit(-1);
			break;

		case Processor.exceptionOverflow:
			Lib.debug(dbgProcess, "Overflow exception");
			handleExit(-1);
			break;

		case Processor.exceptionPageFault:
			Lib.debug(dbgProcess, "Page fault exception");
			handleExit(-1);
			break;

		case Processor.exceptionReadOnly:
			Lib.debug(dbgProcess, "Read-only exception");
			handleExit(-1);
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			handleExit(-1);
			break;
		}
	}

	protected Coff coff;
	protected TranslationEntry[] pageTable;
	protected int numPages;
	protected final int stackPages = 8;
	protected UThread thread;
	private int initialPC, initialSP;
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

class Pipe {
	private byte[] buffer = new byte[4096];
	private int readPos = 0;
	private int writePos = 0;
	private int count = 0;
	private Lock lock = new Lock();
	private Condition2 notEmpty = new Condition2(lock);
	private Condition2 notFull = new Condition2(lock);
	private boolean writerClosed = false;
	private boolean readerClosed = false;
	
	public int read(byte[] data, int offset, int length) {
		lock.acquire();
		
		while (count == 0 && !writerClosed) {
			notEmpty.sleep();
		}
		
		if (count == 0 && writerClosed) {
			lock.release();
			return 0;
		}
		
		int bytesRead = Math.min(length, count);
		for (int i = 0; i < bytesRead; i++) {
			data[offset + i] = buffer[readPos];
			readPos = (readPos + 1) % buffer.length;
		}
		count -= bytesRead;
		
		notFull.wake();
		lock.release();
		return bytesRead;
	}
	
	public int write(byte[] data, int offset, int length) {
		lock.acquire();
		
		if (readerClosed) {
			lock.release();
			return -1;
		}
		
		int bytesWritten = 0;
		while (bytesWritten < length && !readerClosed) {
			while (count == buffer.length && !readerClosed) {
				notFull.sleep();
			}
			
			if (readerClosed) {
				lock.release();
				return -1;
			}
			
			int toWrite = Math.min(length - bytesWritten, buffer.length - count);
			for (int i = 0; i < toWrite; i++) {
				buffer[writePos] = data[offset + bytesWritten + i];
				writePos = (writePos + 1) % buffer.length;
			}
			count += toWrite;
			bytesWritten += toWrite;
			
			notEmpty.wake();
		}
		
		lock.release();
		return bytesWritten;
	}
	
	public void closeWriter() {
		lock.acquire();
		writerClosed = true;
		notEmpty.wakeAll();
		lock.release();
	}
	
	public void closeReader() {
		lock.acquire();
		readerClosed = true;
		notFull.wakeAll();
		lock.release();
	}
}

class PipeFile extends OpenFile {
	private Pipe pipe;
	private boolean isWriter;
	
	public PipeFile(Pipe pipe, boolean isWriter) {
		super(null, "pipe");
		this.pipe = pipe;
		this.isWriter = isWriter;
	}
	
	public int read(byte[] buf, int offset, int length) {
		if (isWriter) return -1;
		return pipe.read(buf, offset, length);
	}
	
	public int write(byte[] buf, int offset, int length) {
		if (!isWriter) return -1;
		return pipe.write(buf, offset, length);
	}
	
	public void close() {
		if (isWriter) {
			pipe.closeWriter();
		} else {
			pipe.closeReader();
		}
	}
}
