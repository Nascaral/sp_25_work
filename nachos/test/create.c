/**

 * This program creates a new file and verifies if the file descriptor is valid.
 */

#include "syscall.h"

int main() {
    char filename[] = "test_create.txt";
    int fd;
    
    // Create a new file using creat syscall
    fd = creat(filename);
    
    // Check if file creation was successful
    if (fd < 0) {
        // File creation failed
        write(fdStandardOutput, "File creation failed\n", 21);
        exit(1);
    } else {
        // File creation successful
        write(fdStandardOutput, "File created successfully: fd = ", 31);
        
        // Convert fd to a character and print it
        char fd_char = '0' + fd;
        write(fdStandardOutput, &fd_char, 1);
        write(fdStandardOutput, "\n", 1);
        
        // Close the file
        close(fd);
    }
    
    return 0;
}