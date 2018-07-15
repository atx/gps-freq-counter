
#include "pps.h"


struct pps_state pps_state = {
	.values = { 0 },
	.timestamp = 0,
	.time_diff = 0,
};


__attribute__((weak))
void pps_update_handler() {}


void pps_handler()
{
	for (unsigned int i = 0; i < PPS_CHANNEL_COUNT; i++) {
		pps_state.values[i] = PPS_REG_BASE[i];
	}
	timems_t now = time_ms();
	pps_state.time_diff = now - pps_state.timestamp;
	pps_state.timestamp = now;

	pps_update_handler();
}
