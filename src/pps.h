
#pragma once

#include <stdint.h>

#include "regs.h"

#define PPS_CHANNEL_COUNT	2

struct pps_state {
	uint32_t values[PPS_CHANNEL_COUNT];
	timems_t timestamp;
	timems_t time_diff;
};

extern struct pps_state pps_state;


void pps_handler();
