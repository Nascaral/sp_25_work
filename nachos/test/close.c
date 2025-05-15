#include "syscall.h"

int main() {
    // Test closing an invalid file descriptor
    int result = Close(-1);
    if (result >= 0) {
        Exit(1); // Should fail with invalid fd
    }
    
    // Create and close a file
    int fd = Create("testfile.txt");
    if (fd < 0) {
        Exit(1);
    }
    
    result = Close(fd);
    if (result < 0) {
        Exit(1); // Should succeed
    }
    
    // Test closing an already closed file
    result = Close(fd);
    if (result >= 0) {
        Exit(1); // Should fail when file is already closed
    }
    
    Exit(0);
} 
