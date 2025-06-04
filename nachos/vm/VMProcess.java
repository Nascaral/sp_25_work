package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

public class VMProcess extends UserProcess {
    private HashMap<Integer, Integer> vpnToSwapPage;
    private Lock processLock;
    
    public VMProcess() {
        super();
        vpnToSwapPage = new HashMap<>();
        processLock = new Lock();
    }

    public void saveState() {
        super.saveState();
    }

    public void restoreState() {
        super.restoreState();
    }

    protected boolean loadSections() {
        pageTable = new TranslationEntry[numPages];
        for (int i = 0; i < numPages; i++) {
            pageTable[i] = new TranslationEntry(i, 0, false, false, false, false);
        }
        
        VMKernel.registerProcess(this);
        return true;
    }

    protected void unloadSections() {
        processLock.acquire();
        
        for (int i = 0; i < pageTable.length; i++) {
            if (pageTable[i] != null && pageTable[i].valid) {
                VMKernel.freePhysicalPage(pageTable[i].ppn);
            }
            
            if (vpnToSwapPage.containsKey(i)) {
                VMKernel.freeSwapPage(vpnToSwapPage.get(i));
            }
        }
        
        vpnToSwapPage.clear();
        processLock.release();
        
        VMKernel.unregisterProcess(this);
    }

    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
        case Processor.exceptionPageFault:
            int vaddr = processor.readRegister(Processor.regBadVAddr);
            handlePageFault(vaddr);
            break;
        default:
            super.handleException(cause);
            break;
        }
    }
    
    private void handlePageFault(int vaddr) {
        int vpn = vaddr / pageSize;
        
        if (vpn < 0 || vpn >= pageTable.length) {
            handleExit(-1);
            return;
        }
        
        VMKernel.getVMLock().acquire();
        
        if (pageTable[vpn].valid) {
            VMKernel.getVMLock().release();
            return;
        }
        
        int ppn = VMKernel.allocatePhysicalPage(this, vpn);
        if (ppn == -1) {
            VMKernel.getVMLock().release();
            handleExit(-1);
            return;
        }
        
        pageTable[vpn].ppn = ppn;
        
        processLock.acquire();
        boolean loaded = false;
        
        if (vpnToSwapPage.containsKey(vpn)) {
            VMKernel.getVMLock().release();
            
            int swapPage = vpnToSwapPage.get(vpn);
            VMKernel.readPageFromSwap(swapPage, ppn);
            loaded = true;
            
            VMKernel.getVMLock().acquire();
        }
        
        if (!loaded) {
            VMKernel.getVMLock().release();
            
            loaded = loadPageFromCoff(vpn, ppn);
            if (!loaded) {
                VMKernel.zeroFillPage(ppn);
            }
            
            VMKernel.getVMLock().acquire();
        }
        
        pageTable[vpn].valid = true;
        pageTable[vpn].used = false;
        pageTable[vpn].dirty = false;
        
        boolean isReadOnly = false;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (vpn >= section.getFirstVPN() && 
                vpn < section.getFirstVPN() + section.getLength()) {
                isReadOnly = section.isReadOnly();
                break;
            }
        }
        pageTable[vpn].readOnly = isReadOnly;
        
        processLock.release();
        VMKernel.getVMLock().release();
    }
    
    private boolean loadPageFromCoff(int vpn, int ppn) {
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            int firstVPN = section.getFirstVPN();
            
            if (vpn >= firstVPN && vpn < firstVPN + section.getLength()) {
                section.loadPage(vpn - firstVPN, ppn);
                return true;
            }
        }
        return false;
    }
    
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();
        int amount = 0;
        
        while (length > 0 && vaddr < numPages * pageSize) {
            int vpn = vaddr / pageSize;
            int addrOffset = vaddr % pageSize;
            int transfer = Math.min(length, pageSize - addrOffset);
            
            if (vpn < 0 || vpn >= pageTable.length) {
                break;
            }
            
            VMKernel.getVMLock().acquire();
            
            if (!pageTable[vpn].valid) {
                VMKernel.getVMLock().release();
                handlePageFault(vaddr);
                VMKernel.getVMLock().acquire();
                
                if (!pageTable[vpn].valid) {
                    VMKernel.getVMLock().release();
                    break;
                }
            }
            
            int ppn = pageTable[vpn].ppn;
            VMKernel.pinPage(ppn);
            VMKernel.getVMLock().release();
            
            int paddr = ppn * pageSize + addrOffset;
            System.arraycopy(memory, paddr, data, offset, transfer);
            
            pageTable[vpn].used = true;
            
            VMKernel.unpinPage(ppn);
            
            vaddr += transfer;
            offset += transfer;
            amount += transfer;
            length -= transfer;
        }
        
        return amount;
    }
    
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();
        int amount = 0;
        
        while (length > 0 && vaddr < numPages * pageSize) {
            int vpn = vaddr / pageSize;
            int addrOffset = vaddr % pageSize;
            int transfer = Math.min(length, pageSize - addrOffset);
            
            if (vpn < 0 || vpn >= pageTable.length) {
                break;
            }
            
            VMKernel.getVMLock().acquire();
            
            if (!pageTable[vpn].valid) {
                VMKernel.getVMLock().release();
                handlePageFault(vaddr);
                VMKernel.getVMLock().acquire();
                
                if (!pageTable[vpn].valid) {
                    VMKernel.getVMLock().release();
                    break;
                }
            }
            
            if (pageTable[vpn].readOnly) {
                VMKernel.getVMLock().release();
                break;
            }
            
            int ppn = pageTable[vpn].ppn;
            VMKernel.pinPage(ppn);
            VMKernel.getVMLock().release();
            
            int paddr = ppn * pageSize + addrOffset;
            System.arraycopy(data, offset, memory, paddr, transfer);
            
            pageTable[vpn].used = true;
            pageTable[vpn].dirty = true;
            
            VMKernel.unpinPage(ppn);
            
            vaddr += transfer;
            offset += transfer;
            amount += transfer;
            length -= transfer;
        }
        
        return amount;
    }
    
    public TranslationEntry getPageTableEntry(int vpn) {
        if (vpn < 0 || vpn >= pageTable.length) {
            return null;
        }
        return pageTable[vpn];
    }
    
    public void setSwapPage(int vpn, int swapPage) {
        processLock.acquire();
        vpnToSwapPage.put(vpn, swapPage);
        processLock.release();
    }
    
    public boolean hasSwapPage(int vpn) {
        processLock.acquire();
        boolean result = vpnToSwapPage.containsKey(vpn);
        processLock.release();
        return result;
    }
    
    public int getSwapPage(int vpn) {
        processLock.acquire();
        Integer swapPage = vpnToSwapPage.get(vpn);
        processLock.release();
        return (swapPage != null) ? swapPage : -1;
    }

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}