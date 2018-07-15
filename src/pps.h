
#pragma once

#include <stdint.h>

#include "regs.h"

struct pps_state {
	uint32_t value;
	timems_t timestamp;
	timems_t time_diff;
};

extern struct pps_state pps_state;


void pps_handler();
