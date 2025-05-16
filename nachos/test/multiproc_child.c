#include "syscall.h"

#define BUFFER_SIZE 1024  // 1 page worth of data

int main(int argc, char *argv[]) {
    char buffer[BUFFER_SIZE];
    int i;
    int processNum;
    
    // Check arguments
    if (argc != 2) {
        write(fdStandardOutput, "Error: Invalid number of arguments\n", 34);
        exit(1);
    }
    
    // Get process number from argument
    processNum = argv[1][0] - '0';
    
    // Fill buffer with process-specific data
    for (i = 0; i < BUFFER_SIZE - 1; i++) {
        buffer[i] = 'A' + processNum;
    }
    buffer[BUFFER_SIZE - 1] = '\0';
    
    // Create a unique file for this process
    char filename[20];
    for (i = 0; i < 19; i++) {
        filename[i] = '0' + processNum;
    }
    filename[19] = '\0';
    
    // Write to file
    int fd = creat(filename);
    if (fd < 0) {
        write(fdStandardOutput, "Error: Failed to create file\n", 29);
        exit(1);
    }
    
    // Write buffer to file
    int bytesWritten = write(fd, buffer, BUFFER_SIZE);
    if (bytesWritten != BUFFER_SIZE) {
        write(fdStandardOutput, "Error: Failed to write complete buffer\n", 38);
        close(fd);
        exit(1);
    }
    
    // Close file
    close(fd);
    
    // Exit with process number as status
    exit(processNum);
} 