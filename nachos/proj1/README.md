No members in group, working solo.
Code written: I wrote code that handles concurrency by implementing threading in KThread (added fork, join, and yeild to manage thread lifecycle and make sure that it correctly schedules and switches), Alarm (Added sleep mechanism using waitUntil to block calling threads
for x number of clock ticks and wakes them up accordingly), Condition2 (Used semaphores to support sleep, wake, and wakeALL), and Rendezvous (Allowed thread pairs to exchange data at a rendezvous point).
It worked pretty well after some hard testing and debugging which was mainly done in ThreadedKernel.java selfTest.
