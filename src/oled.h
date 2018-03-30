
#pragma once

#include <stdint.h>

#define OLED_HEIGHT 64
#define OLED_WIDTH 128

void oled_init();
void oled_command(uint8_t cmd);
void oled_draw_pixel(unsigned int x, unsigned int y, bool on);
void oled_clear();
void oled_flush();
