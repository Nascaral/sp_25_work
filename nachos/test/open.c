#include "syscall.h"

int main() {
    // Test opening a non-existent file
    int fd = Open("nonexistent.txt");
    if (fd >= 0) {
        Exit(1); // Should fail when file doesn't exist
    }
    
    // Create and then open a file
    int createFd = Create("testfile.txt");
    if (createFd < 0) {
        Exit(1); // Failed to create file
    }
    
    int openFd = Open("testfile.txt");
    if (openFd < 0) {
        Exit(1); // Failed to open existing file
    }
    
    // Test opening with invalid name
    int invalidFd = Open("");
    if (invalidFd >= 0) {
        Exit(1); // Should fail with empty name
    }
    
    Exit(0);
} 
