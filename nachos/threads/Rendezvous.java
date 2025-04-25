package nachos.threads;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.*;


public class Rendezvous {
    private final Lock lock = new Lock();
    private final HashMap<Integer, LinkedList<Slot>> waiting = new HashMap<>();

    private static class Slot {
        final int     tag;
        int           item;      
        final KThread thread;

        Slot(int tag, int item, KThread t) {
            this.tag    = tag;
            this.item   = item;
            this.thread = t;
        }
    }

    public Rendezvous() {
    }

    public int exchange(int tag, int myItem) {          // FIX: parameter order & return type
        lock.acquire();

        LinkedList<Slot> queue =
            waiting.computeIfAbsent(tag, k -> new LinkedList<>());

     
        if (!queue.isEmpty()) {
            Slot partner = queue.removeFirst();
            int ret      = partner.item;   // value to return to me
            partner.item = myItem;         // give mine to partner
            lock.release();                // release lock before waking partner
            
            boolean intStatus = Machine.interrupt().disable();
            partner.thread.ready();        // wake partner
            Machine.interrupt().restore(intStatus);
            
            return ret;
        }

        /* Otherwise, enqueue myself and go to sleep. */
        Slot me = new Slot(tag, myItem, KThread.currentThread());
        queue.addLast(me);

        boolean intStatus = Machine.interrupt().disable();
        lock.release();                    // let a partner in
        KThread.sleep();                   // block
        Machine.interrupt().restore(intStatus);

      
        return me.item;
    }
}
