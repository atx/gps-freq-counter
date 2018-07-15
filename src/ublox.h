
#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "regs.h"


struct ublox_state {
	uint32_t n_sats;
	bool has_fix;
	timems_t last_update;
};

extern struct ublox_state ublox_state;


void uart_process_rx();
void uart_process_tx();
