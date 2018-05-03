
#pragma once

#include <stdbool.h>
#include <string.h>

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

static inline void str_format_int(char *str, int i)
{
	// Assumes the caller has long enough buffer
	if (i < 0) {
		*str++ = '-';
		i *= -1;
	}
	char *start = str;
	do {
		*str++ = '0' + (i % 10);
		i /= 10;
	} while (i != 0);
	*str = '\0';

	size_t len = strlen(start);
	for (unsigned int i = 0; i < len / 2; i++) {
		char tmp = start[i];
		start[i] = start[len - i - 1];
		start[len - i - 1] = tmp;
	}
}
