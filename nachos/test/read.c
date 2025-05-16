/**
 * Comprehensive test program for all file management syscalls in Nachos.
 * This program tests creat, open, read, write, close, and unlink syscalls together.
 */

#include "syscall.h"

int main() {
    char filename[] = "comprehensive_test.txt";
    int fd;
    char buffer[100];
    int result;
    
    write(fdStandardOutput, "===== COMPREHENSIVE FILE SYSCALL TEST =====\n", 43);
    
    // TEST 1: Create a file
    write(fdStandardOutput, "Test 1: Creating file...\n", 25);
    fd = creat(filename);
    if (fd < 0) {
        write(fdStandardOutput, "FAILED: File creation failed\n", 28);
        exit(1);
    } else {
        write(fdStandardOutput, "SUCCESS: File created with fd = ", 32);
        char fd_char = '0' + fd;
        write(fdStandardOutput, &fd_char, 1);
        write(fdStandardOutput, "\n", 1);
    }
    
    // TEST 2: Write to the file
    write(fdStandardOutput, "Test 2: Writing to file...\n", 27);
    char content[] = "This is content for the comprehensive test";
    result = write(fd, content, sizeof(content) - 1);
    if (result != sizeof(content) - 1) {
        write(fdStandardOutput, "FAILED: Write operation failed\n", 30);
        close(fd);
        exit(1);
    } else {
        write(fdStandardOutput, "SUCCESS: Wrote content to file\n", 31);
    }
    
    // TEST 3: Close the file
    write(fdStandardOutput, "Test 3: Closing file...\n", 24);
    result = close(fd);
    if (result < 0) {
        write(fdStandardOutput, "FAILED: File close failed\n", 26);
        exit(1);
    } else {
        write(fdStandardOutput, "SUCCESS: File closed\n", 21);
    }
    
    // TEST 4: Open the file again
    write(fdStandardOutput, "Test 4: Opening file...\n", 24);
    fd = open(filename);
    if (fd < 0) {
        write(fdStandardOutput, "FAILED: File open failed\n", 25);
        exit(1);
    } else {
        write(fdStandardOutput, "SUCCESS: File opened with fd = ", 31);
        char fd_char = '0' + fd;
        write(fdStandardOutput, &fd_char, 1);
        write(fdStandardOutput, "\n", 1);
    }
    
    // TEST 5: Read from the file
    write(fdStandardOutput, "Test 5: Reading from file...\n", 29);
    int bytesRead = read(fd, buffer, 99);
    if (bytesRead < 0) {
        write(fdStandardOutput, "FAILED: Read operation failed\n", 30);
        close(fd);
        exit(1);
    } else {
        buffer[bytesRead] = '\0';
        write(fdStandardOutput, "SUCCESS: Read content: ", 23);
        write(fdStandardOutput, buffer, bytesRead);
        write(fdStandardOutput, "\n", 1);
    }
    
    // TEST 6: Close the file again
    write(fdStandardOutput, "Test 6: Closing file again...\n", 30);
    result = close(fd);
    if (result < 0) {
        write(fdStandardOutput, "FAILED: File close failed\n", 26);
        exit(1);
    } else {
        write(fdStandardOutput, "SUCCESS: File closed\n", 21);
    }
    
    // TEST 7: Unlink the file
    write(fdStandardOutput, "Test 7: Unlinking file...\n", 26);
    result = unlink(filename);
    if (result < 0) {
        write(fdStandardOutput, "FAILED: File unlink failed\n", 27);
        exit(1);
    } else {
        write(fdStandardOutput, "SUCCESS: File unlinked\n", 23);
    }
    
    // TEST 8: Try to open the unlinked file
    write(fdStandardOutput, "Test 8: Opening unlinked file...\n", 33);
    fd = open(filename);
    if (fd < 0) {
        write(fdStandardOutput, "SUCCESS: Cannot open unlinked file (as expected)\n", 48);
    } else {
        write(fdStandardOutput, "FAILED: Unlinked file could still be opened!\n", 45);
        close(fd);
    }
    
    write(fdStandardOutput, "===== ALL TESTS COMPLETED =====\n", 33);
    return 0;
}