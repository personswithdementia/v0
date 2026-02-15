
#include <stdio.h>
#include <stdlib.h>


enum Errors {
    Monday, Tuesday,
};

#define LOG(...) vprintf(stderr, __VA_ARGS__)

int main(void) { return EXIT_SUCCESS; }