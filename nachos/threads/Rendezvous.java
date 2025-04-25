package nachos.threads;

import nachos.machine.*;

public class Rendezvous {
   private int[] items;
    private boolean[] hasItem;
    private Lock lock;
    private Condition[] conditions;
    
    public Rendezvous(int n) {
        items = new int[n];
        hasItem = new boolean[n];
        lock = new Lock();
        conditions = new Condition[n];
        for( int i =0; i < n; i++){
            conditions[i] = new Condition(lock);
        }
    }

    public int exchange(int myItem, int tag){
        lock.acquire();

        if (hasItem[tag]){
            int otherItem = items[tag];
            hasItem[tag] = false;
            conditions[tag].wake();
            lock.release();
            return otherItem;
        } else {
            items[tag] = true;
            conditions[tag].sleep();
            int otherItem = items[tag];
            hasItem[tag] = false;
            lock.release();
            return otherItem;
        }
    }
}

