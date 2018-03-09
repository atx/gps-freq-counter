
#include <stdlib.h>
#include <stdint.h>

#include "utils.h"

#define TESTREG (*((volatile uint32_t *)0x40000000))

uint8_t data[100];


__attribute__((noinline))
void fill_data()
{
	int b = 0;
	int a = 1;
	for (int i = 0; i < ARRAY_SIZE(data); i++) {
		int n = a + b;
		b = a;
		a = n;
		data[i] = a % 256;
	}
}


__attribute__((noinline))
uint32_t sum_data(uint8_t *d, size_t len)
{
	uint32_t ret = 0;
	for (size_t i = 0; i < len; i++) {
		ret += d[i];
	}
	return ret;
}



void main()
{
	fill_data();
	TESTREG = sum_data(data, ARRAY_SIZE(data));
}
