#include "syscall.h"

int main() {
    // Test joining a non-existent process
    int status;
    int result = Join(-1, &status);
    if (result >= 0) {
        Exit(1); // Should fail with invalid pid
    }
    
    // Create a child process and join it
    int pid = Exec("test/halt.coff");
    if (pid < 0) {
        Exit(1);
    }
    
    result = Join(pid, &status);
    if (result < 0) {
        Exit(1); // Should succeed
    }
    
    // Test joining the same process twice
    result = Join(pid, &status);
    if (result >= 0) {
        Exit(1); // Should fail when process is already joined
    }
    
    // Test joining with invalid status pointer
    pid = Exec("test/halt.coff");
    if (pid < 0) {
        Exit(1);
    }
    
    result = Join(pid, 0);
    if (result < 0) {
        Exit(1); // Should succeed even with NULL status
    }
    
    Exit(0);
} 
