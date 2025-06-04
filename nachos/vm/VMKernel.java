package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

public class VMKernel extends UserKernel {
    private static Lock vmLock;
    private static HashMap<Integer, VMProcess> processMap;
    private static InvertedPageTable pageTable;
    private static OpenFile swapFile;
    private static int swapPageCounter;
    private static LinkedList<Integer> freeSwapPages;
    private static Lock swapLock;
    private static Condition2 pagesAvailable;
    private static int clockHand;
    
    public VMKernel() {
        super();
    }

    public void initialize(String[] args) {
        super.initialize(args);
        
        vmLock = new Lock();
        processMap = new HashMap<>();
        pageTable = new InvertedPageTable(Machine.processor().getNumPhysPages());
        
        swapFile = ThreadedKernel.fileSystem.open("SWAP_FILE_NACHOS", true);
        if (swapFile == null) {
            Lib.debug(dbgVM, "Failed to create swap file");
            Lib.assertNotReached();
        }
        
        swapPageCounter = 0;
        freeSwapPages = new LinkedList<>();
        swapLock = new Lock();
        pagesAvailable = new Condition2(vmLock);
        clockHand = 0;
    }

    public void selfTest() {
        super.selfTest();
    }

    public void run() {
        super.run();
    }

    public void terminate() {
        if (swapFile != null) {
            swapFile.close();
            ThreadedKernel.fileSystem.remove("SWAP_FILE_NACHOS");
        }
        super.terminate();
    }
    
    public static void registerProcess(VMProcess process) {
        vmLock.acquire();
        processMap.put(process.getPID(), process);
        vmLock.release();
    }
    
    public static void unregisterProcess(VMProcess process) {
        vmLock.acquire();
        processMap.remove(process.getPID());
        vmLock.release();
    }
    
    public static int allocatePhysicalPage(VMProcess process, int vpn) {
        vmLock.acquire();
        
        int ppn = -1;
        for (int i = 0; i < pageTable.size(); i++) {
            if (!pageTable.isOccupied(i)) {
                ppn = i;
                pageTable.setEntry(i, process, vpn);
                break;
            }
        }
        
        if (ppn == -1) {
            ppn = evictPage();
            if (ppn != -1) {
                pageTable.setEntry(ppn, process, vpn);
            }
        }
        
        vmLock.release();
        return ppn;
    }
    
    private static int evictPage() {
        int numPages = pageTable.size();
        int startHand = clockHand;
        
        while (true) {
            int currentHand = clockHand;
            clockHand = (clockHand + 1) % numPages;
            
            if (pageTable.isPinned(currentHand)) {
                if (clockHand == startHand) {
                    while (pageTable.isPinned(currentHand)) {
                        pagesAvailable.sleep();
                        currentHand = clockHand;
                        clockHand = (clockHand + 1) % numPages;
                    }
                }
                continue;
            }
            
            VMProcessInfo info = pageTable.getProcessInfo(currentHand);
            if (info == null) continue;
            
            VMProcess owner = processMap.get(info.pid);
            if (owner == null) continue;
            
            TranslationEntry te = owner.getPageTableEntry(info.vpn);
            if (te == null || !te.valid) continue;
            
            if (te.used) {
                te.used = false;
                if (clockHand == startHand) {
                    continue;
                }
            } else {
                if (te.dirty) {
                    int swapPage = allocateSwapPage();
                    if (swapPage != -1) {
                        writePageToSwap(currentHand, swapPage);
                        owner.setSwapPage(info.vpn, swapPage);
                    }
                } else if (owner.hasSwapPage(info.vpn)) {
                } else {
                }
                
                te.valid = false;
                pageTable.clearEntry(currentHand);
                return currentHand;
            }
        }
    }
    
    public static void pinPage(int ppn) {
        vmLock.acquire();
        pageTable.pinPage(ppn);
        vmLock.release();
    }
    
    public static void unpinPage(int ppn) {
        vmLock.acquire();
        pageTable.unpinPage(ppn);
        pagesAvailable.wakeAll();
        vmLock.release();
    }
    
    public static int allocateSwapPage() {
        swapLock.acquire();
        int swapPage;
        
        if (!freeSwapPages.isEmpty()) {
            swapPage = freeSwapPages.removeFirst();
        } else {
            swapPage = swapPageCounter++;
        }
        
        swapLock.release();
        return swapPage;
    }
    
    public static void freeSwapPage(int swapPage) {
        swapLock.acquire();
        freeSwapPages.add(swapPage);
        swapLock.release();
    }
    
    public static void writePageToSwap(int ppn, int swapPage) {
        byte[] memory = Machine.processor().getMemory();
        byte[] buffer = new byte[pageSize];
        
        System.arraycopy(memory, ppn * pageSize, buffer, 0, pageSize);
        
        int bytesWritten = swapFile.write(swapPage * pageSize, buffer, 0, pageSize);
        Lib.assertTrue(bytesWritten == pageSize);
    }
    
    public static void readPageFromSwap(int swapPage, int ppn) {
        byte[] memory = Machine.processor().getMemory();
        byte[] buffer = new byte[pageSize];
        
        int bytesRead = swapFile.read(swapPage * pageSize, buffer, 0, pageSize);
        Lib.assertTrue(bytesRead == pageSize);
        
        System.arraycopy(buffer, 0, memory, ppn * pageSize, pageSize);
    }
    
    public static void zeroFillPage(int ppn) {
        byte[] memory = Machine.processor().getMemory();
        Arrays.fill(memory, ppn * pageSize, (ppn + 1) * pageSize, (byte)0);
    }
    
    public static Lock getVMLock() {
        return vmLock;
    }
    
    public static void freePhysicalPage(int ppn) {
        vmLock.acquire();
        pageTable.clearEntry(ppn);
        vmLock.release();
    }
    
    private static class InvertedPageTable {
        private VMProcessInfo[] entries;
        private boolean[] pinned;
        
        public InvertedPageTable(int size) {
            entries = new VMProcessInfo[size];
            pinned = new boolean[size];
        }
        
        public int size() {
            return entries.length;
        }
        
        public boolean isOccupied(int ppn) {
            return entries[ppn] != null;
        }
        
        public void setEntry(int ppn, VMProcess process, int vpn) {
            entries[ppn] = new VMProcessInfo(process.getPID(), vpn);
        }
        
        public void clearEntry(int ppn) {
            entries[ppn] = null;
            pinned[ppn] = false;
        }
        
        public VMProcessInfo getProcessInfo(int ppn) {
            return entries[ppn];
        }
        
        public void pinPage(int ppn) {
            pinned[ppn] = true;
        }
        
        public void unpinPage(int ppn) {
            pinned[ppn] = false;
        }
        
        public boolean isPinned(int ppn) {
            return pinned[ppn];
        }
    }
    
    private static class VMProcessInfo {
        public int pid;
        public int vpn;
        
        public VMProcessInfo(int pid, int vpn) {
            this.pid = pid;
            this.vpn = vpn;
        }
    }

    private static VMProcess dummy1 = null;
    private static final char dbgVM = 'v';
    private static final int pageSize = Processor.pageSize;
}