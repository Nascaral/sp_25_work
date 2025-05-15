#include "syscall.h"

int main() {
    // Test normal exit
    Exit(0);
    
    // This should never be reached
    Exit(1);
} 
