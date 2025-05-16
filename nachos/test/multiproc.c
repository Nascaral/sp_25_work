
#include "syscall.h"

#define NUM_PROCESSES 3
#define BUFFER_SIZE   1024    /* not used here, kept for clarity */

int
main(void)
{
    int pids[NUM_PROCESSES];
    int status;
    int i;

    for (i = 0; i < NUM_PROCESSES; ++i) {
        /* build a tiny argv table on the parent’s stack */
        char procNum[2];
        procNum[0] = '0' + i;
        procNum[1] = '\0';

        char *argv[2];
        argv[0] = procNum;   /* first (and only) argument            */
        argv[1] = 0;         /* NULL-terminate argv table            */

        /* exec(child, argc=1, argv=argv) */
        pids[i] = exec("multiproc_child.coff", 1, argv);
        if (pids[i] < 0) {
            write(fdStandardOutput,
                  "Error: Failed to create child process\n", 37);
            exit(1);
        }
    }

    /* wait for the children and check their exit codes */
    for (i = 0; i < NUM_PROCESSES; ++i) {
        if (join(pids[i], &status) < 0) {
            write(fdStandardOutput,
                  "Error: Failed to join child\n", 28);
            exit(1);
        }
        if (status != i) {
            write(fdStandardOutput,
                  "Error: Child returned wrong status\n", 34);
            exit(1);
        }
    }

    write(fdStandardOutput,
          "Success: All child processes completed\n", 40);
    return 0;
}
