
#include "syscall.h"

#define BUFFER_SIZE 1024

int
main(int argc, char *argv[])
{
    if (argc != 1) {                     /* exactly one argument */
        write(fdStandardOutput,
              "Error: Invalid number of arguments\n", 34);
        exit(1);
    }

    int procNum = argv[0][0] - '0';

    char buf[BUFFER_SIZE];
    int i;
    for (i = 0; i < BUFFER_SIZE - 1; ++i)
        buf[i] = 'A' + procNum;
    buf[BUFFER_SIZE-1] = '\0';

    char filename[] = { 'f','i','l','e','_',
                        (char)('0' + procNum), '\0' };

    int fd = creat(filename);
    if (fd < 0) {
        write(fdStandardOutput, "Error: creat failed\n", 20);
        exit(1);
    }
    write(fd, buf, BUFFER_SIZE);
    close(fd);

    exit(procNum);                       /* return status == procNum */
}
