
#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "utils.h"


void ui_init();
void ui_on_key_down();
void ui_on_key_up();
void ui_on_pps(uint32_t count);
void ui_on_frame();
void ui_on_gps_update(bool has_fix, unsigned int n_sats);
