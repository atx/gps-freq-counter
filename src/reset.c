
#include <stdint.h>

void main();

void reset_handler()
{
	// TODO: .bss/.data init

	main();
	while (1) {}
}

__attribute__((weak))
uint32_t *irq_handler(uint32_t *regs, uint32_t irq)
{
	// Do nothing by default
	return regs;
}
