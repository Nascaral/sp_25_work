/**
 * This program creates a file, writes to it, unlinks it, and then tries to open it again.
 */

#include "syscall.h"

int main() {
    char filename[] = "test_unlink.txt";
    int fd;
    
    // Create a new file
    fd = creat(filename);
    if (fd < 0) {
        write(fdStandardOutput, "File creation failed\n", 21);
        exit(1);
    }
    
    // Write something to the file
    char message[] = "This file will be deleted";
    write(fd, message, sizeof(message) - 1);
    
    // Close the file
    close(fd);
    
    // Verify file exists by opening it
    fd = open(filename);
    if (fd < 0) {
        write(fdStandardOutput, "File doesn't exist before unlink (error)\n", 41);
        exit(1);
    } else {
        write(fdStandardOutput, "File exists before unlink (correct)\n", 36);
        close(fd);
    }
    
    // Delete the file using unlink syscall
    int result = unlink(filename);
    if (result < 0) {
        write(fdStandardOutput, "Failed to unlink file\n", 22);
        exit(1);
    } else {
        write(fdStandardOutput, "File unlinked successfully\n", 27);
    }
    
    // Try to open the file again (should fail because file was unlinked)
    fd = open(filename);
    if (fd < 0) {
        write(fdStandardOutput, "File doesn't exist after unlink (correct)\n", 42);
    } else {
        write(fdStandardOutput, "ERROR: File still exists after unlink!\n", 39);
        close(fd);
    }
    
    // Test unlinking a file that doesn't exist (should fail)
    char nonexistentFile[] = "nonexistent.txt";
    result = unlink(nonexistentFile);
    if (result < 0) {
        write(fdStandardOutput, "As expected, cannot unlink nonexistent file\n", 44);
    } else {
        write(fdStandardOutput, "ERROR: Unlink of nonexistent file succeeded!\n", 45);
    }
    
    return 0;
}