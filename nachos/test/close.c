/**
 * This program creates a file, closes it, then attempts to use the file descriptor after closing.
 */

#include "syscall.h"

int main() {
    char filename[] = "test_close.txt";
    int fd;
    char buffer[10];
    
    // Create a new file
    fd = creat(filename);
    if (fd < 0) {
        write(fdStandardOutput, "File creation failed\n", 21);
        exit(1);
    }
    
    // Write something to the file
    char message[] = "Test data";
    write(fd, message, sizeof(message) - 1);
    
    // Close the file
    int result = close(fd);
    if (result < 0) {
        write(fdStandardOutput, "Failed to close file\n", 21);
        exit(1);
    } else {
        write(fdStandardOutput, "File closed successfully\n", 25);
    }
    
    // Try to write to the file descriptor after closing (should fail)
    result = write(fd, message, sizeof(message) - 1);
    if (result < 0) {
        write(fdStandardOutput, "As expected, cannot write to closed fd\n", 39);
    } else {
        write(fdStandardOutput, "ERROR: Write to closed fd succeeded!\n", 37);
    }
    
    // Try to read from the file descriptor after closing (should fail)
    result = read(fd, buffer, sizeof(buffer) - 1);
    if (result < 0) {
        write(fdStandardOutput, "As expected, cannot read from closed fd\n", 40);
    } else {
        write(fdStandardOutput, "ERROR: Read from closed fd succeeded!\n", 38);
    }
    
    // Open the file again to verify it exists and has the data we wrote
    fd = open(filename);
    if (fd < 0) {
        write(fdStandardOutput, "Failed to reopen file\n", 22);
        exit(1);
    }
    
    // Read back the data
    int bytesRead = read(fd, buffer, sizeof(buffer) - 1);
    buffer[bytesRead] = '\0';
    
    write(fdStandardOutput, "Reopened file, read back: ", 26);
    write(fdStandardOutput, buffer, bytesRead);
    write(fdStandardOutput, "\n", 1);
    
    // Close the file again
    close(fd);
    
    return 0;
}