#include "syscall.h"

int main() {
    // Test creating a new file
    int fd = Create("testfile.txt");
    if (fd < 0) {
        Exit(1); // Failed to create file
    }
    
    // Test creating a file that already exists
    int fd2 = Create("testfile.txt");
    if (fd2 >= 0) {
        Exit(1); // Should fail when file exists
    }
    
    // Test creating with invalid name
    int fd3 = Create("");
    if (fd3 >= 0) {
        Exit(1); // Should fail with empty name
    }
    
    Exit(0);
} 
