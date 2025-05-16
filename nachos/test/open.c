/**
 * Test program for the open() syscall in Nachos.
 * This program attempts to open an existing file and verifies if the file descriptor is valid.
 */

#include "syscall.h"

int main() {
    char filename[] = "test_open.txt";
    int fd1, fd2;
    
    // First create a file to work with
    fd1 = creat(filename);
    if (fd1 < 0) {
        write(fdStandardOutput, "Initial file creation failed\n", 28);
        exit(1);
    }
    
    // Write some data to the file
    char message[] = "This is a test file for open syscall";
    write(fd1, message, sizeof(message) - 1);
    
    // Close the file
    close(fd1);
    
    // Now try to open the file
    fd2 = open(filename);
    if (fd2 < 0) {
        write(fdStandardOutput, "Opening file failed\n", 20);
        exit(1);
    } else {
        write(fdStandardOutput, "File opened successfully: fd = ", 31);
        
        // Convert fd to a character and print it
        char fd_char = '0' + fd2;
        write(fdStandardOutput, &fd_char, 1);
        write(fdStandardOutput, "\n", 1);
        
        // Close the file descriptor
        close(fd2);
    }
    
    return 0;
}