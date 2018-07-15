
#include "pps.h"


struct pps_state pps_state = {
	.value = 0,
	.timestamp = 0,
	.time_diff = 0,
};


__attribute__((weak))
void pps_update_handler() {}


void pps_handler()
{
	pps_state.value = pps_value();
	timems_t now = time_ms();
	pps_state.time_diff = now - pps_state.timestamp;
	pps_state.timestamp = now;

	pps_update_handler();
}
