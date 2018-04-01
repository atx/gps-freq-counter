
#pragma once

#define ARRAY_SIZE(x) (sizeof(x) / sizeof(x[0]))
#define BIT(n) (1 << (n))
#define SELECT_BYTE(v, n) (v >> (8*(n)) & 0xff)

static inline void nop_loop(unsigned long count)
{
	for (unsigned long i = 0; i < count; i++) {
		asm volatile ("nop");
	}
}
