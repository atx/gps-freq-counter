
#pragma once

#include <stdbool.h>

#define ARRAY_SIZE(x) (sizeof(x) / sizeof(x[0]))
#define BIT(n) (1 << (n))
#define SELECT_BYTE(v, n) (v >> (8*(n)) & 0xff)

#define MIN(a, b) ({ \
	__typeof__(a) _a = (a); \
	__typeof__(b) _b = (b); \
	_a > _b ? _b : _a; \
})

#define MAX(a, b) ({ \
	__typeof__(a) _a = (a); \
	__typeof__(b) _b = (b); \
	_a > _b ? _a : _b; \
})

#define CLAMP(v, mi, mx) MIN(MAX(v, mi), mx)

static inline void nop_loop(unsigned long count)
{
	for (unsigned long i = 0; i < count; i++) {
		asm volatile ("nop");
	}
}

static inline bool str_startswith(const char *haystack, const char *needle)
{
	while (*needle != '\0' && *haystack != '\0') {
		if (*haystack++ != *needle++) {
			return false;
		}
	}
	return *needle == '\0';
}
