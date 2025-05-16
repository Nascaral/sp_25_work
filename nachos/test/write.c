/**
 * Test program for the write() syscall in Nachos.
 * This program writes to a file and to standard output.
 */

#include "syscall.h"

int main() {
    char filename[] = "test_write.txt";
    int fd;
    int bytesWritten;
    
    // Create a new file for writing
    fd = creat(filename);
    if (fd < 0) {
        write(fdStandardOutput, "File creation failed\n", 21);
        exit(1);
    }
    
    // Write to the file
    char message[] = "This text is being written to a file using write syscall";
    bytesWritten = write(fd, message, sizeof(message) - 1);
    
    if (bytesWritten != sizeof(message) - 1) {
        write(fdStandardOutput, "Write to file failed\n", 21);
        close(fd);
        exit(1);
    } else {
        write(fdStandardOutput, "Successfully wrote ", 19);
        
        // Convert bytes written to a character representation and print it
        char bytes_char[10];
        int temp = bytesWritten;
        int pos = 0;
        
        // Handle the case when bytesWritten is 0
        if (temp == 0) {
            bytes_char[pos++] = '0';
        } else {
            // Convert integer to string representation
            while (temp > 0) {
                bytes_char[pos++] = '0' + (temp % 10);
                temp /= 10;
            }
        }
        
        // Print in correct order
        while (pos > 0) {
            write(fdStandardOutput, &bytes_char[--pos], 1);
        }
        
        write(fdStandardOutput, " bytes to file\n", 15);
    }
    
    // Close the file
    close(fd);
    
    // Now open the file and read back what was written
    fd = open(filename);
    if (fd < 0) {
        write(fdStandardOutput, "Failed to open file for reading\n", 32);
        exit(1);
    }
    
    char buffer[100];
    int bytesRead = read(fd, buffer, 99);
    if (bytesRead > 0) {
        buffer[bytesRead] = '\0';
        write(fdStandardOutput, "Content read back: ", 19);
        write(fdStandardOutput, buffer, bytesRead);
        write(fdStandardOutput, "\n", 1);
    }
    
    close(fd);
    return 0;
}