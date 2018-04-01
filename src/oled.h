
#pragma once

#include <stdbool.h>
#include <stdint.h>

#define OLED_HEIGHT 64
#define OLED_WIDTH 128

enum oled_blit_mode {
	OLED_BLIT_NORMAL,
	OLED_BLIT_INVERT
};

void oled_init();
void oled_command(uint8_t cmd);
void oled_draw_pixel(unsigned int x, unsigned int y, bool on);
void oled_blit(const uint8_t *d, unsigned int w, unsigned int h,
			   unsigned int x, unsigned int y,
			   enum oled_blit_mode mode);
void oled_fill(unsigned int x, unsigned int y, unsigned int w, unsigned int h, enum oled_blit_mode mode);
void oled_clear();
void oled_flush();
