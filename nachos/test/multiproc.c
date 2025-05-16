#include "syscall.h"

#define NUM_PROCESSES 3
#define BUFFER_SIZE 1024  // 1 page worth of data

void childProcess(int processNum) {
    char buffer[BUFFER_SIZE];
    int i;
    
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

int main() {
    int pids[NUM_PROCESSES];
    int status;
    int i;
    
    // Create multiple child processes
    for (i = 0; i < NUM_PROCESSES; i++) {
        // Create argument string
        char processNum[2];
        processNum[0] = '0' + i;
        processNum[1] = '\0';
        
        // Execute child process with argument
        pids[i] = exec("multiproc_child.coff", 1, processNum);
        if (pids[i] < 0) {
            write(fdStandardOutput, "Error: Failed to create child process\n", 37);
            exit(1);
        }
    }
    
    // Wait for all children to complete
    for (i = 0; i < NUM_PROCESSES; i++) {
        if (join(pids[i], &status) < 0) {
            write(fdStandardOutput, "Error: Failed to join child process\n", 35);
            exit(1);
        }
        
        // Verify exit status
        if (status != i) {
            write(fdStandardOutput, "Error: Child process returned incorrect status\n", 48);
            exit(1);
        }
    }
    
    write(fdStandardOutput, "Success: All child processes completed successfully\n", 52);
    return 0;
} 