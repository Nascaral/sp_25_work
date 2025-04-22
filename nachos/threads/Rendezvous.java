package nachos.threads;
import java.util.*;
import nachos.machine.*;

public class Rendezvous {
    private static class Slot {
        int tag; Object value; KThread thread;
        Slot(int tag,Object value,KThread th){this.tag=tag;this.value=value;this.thread=th;}
    }
    private Lock lock = new Lock();
    private HashMap<Integer,LinkedList<Slot>> waiting = new HashMap<>();

    public Object exchange(int tag,Object myVal){
        lock.acquire();
        LinkedList<Slot> q = waiting.computeIfAbsent(tag,k->new LinkedList<>());

        if (!q.isEmpty()) {                    // partner ready
            Slot partner = q.removeFirst();
            Object ret = partner.value;
            partner.value = myVal;             // give mine to partner
            partner.thread.ready();            // wake partner
            lock.release();
            return ret;
        }

        /* I’m first: park myself */
        Slot me = new Slot(tag,myVal,KThread.currentThread());
        q.add(me);
        boolean intStat = Machine.interrupt().disable();
        lock.release();
        KThread.sleep();                       // block
        Machine.interrupt().restore(intStat);

        // awoken by partner, 'value' field now contains partner’s gift
        return me.value;
    }
}

