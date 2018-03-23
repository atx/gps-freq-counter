
#include <stdint.h>

void main();

extern int _data_load_start;
extern int _data_start;
extern int _data_end;
extern int _bss_start;
extern int _bss_end;

void reset_handler()
{
	int *from = &_data_load_start;
	int *to = &_data_start;
	while (to != &_data_end) {
		*(to++) = *(from++);
	}
	to = &_bss_start;
	while (to != &_bss_end) {
		*(to++) = 0;
	}

	main();
	while (1) {}
}

__attribute__((weak))
uint32_t *irq_handler(uint32_t *regs, uint32_t irq)
{
	// Do nothing by default
	return regs;
}
