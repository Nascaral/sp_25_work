#include "syscall.h"

int main() {
    // Test executing a non-existent program
    int pid = Exec("nonexistent.coff");
    if (pid >= 0) {
        Exit(1); // Should fail when program doesn't exist
    }
    
    // Test executing a valid program
    pid = Exec("test/halt.coff");
    if (pid < 0) {
        Exit(1); // Should succeed
    }
    
    // Test executing with invalid name
    pid = Exec("");
    if (pid >= 0) {
        Exit(1); // Should fail with empty name
    }
    
    // Test executing with invalid arguments
    char *args[1];
    args[0] = 0; // NULL argument
    pid = Exec("test/halt.coff", 1, args);
    if (pid >= 0) {
        Exit(1); // Should fail with invalid arguments
    }
    
    Exit(0);
} 
