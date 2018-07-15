
#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "utils.h"


void ui_init();
void ui_on_key_down();
void ui_on_key_up();
void ui_on_frame();

enum pps_input {
	PPS_INPUT_INTERNAL,
	PPS_INPUT_EXTERNAL
};

void ui_set_input(enum pps_input input);
