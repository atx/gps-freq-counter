
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

#include "regs.h"
#include "utils.h"
#include "oled.h"

void main()
{
	oled_init();
	const unsigned int scale = 8;
	unsigned int shift = 0;
	while (true) {
		output_high(OUTPUT_LED_A);
		for (unsigned int y = 0; y < OLED_HEIGHT; y++) {
			for (unsigned int x = 0; x < OLED_WIDTH; x++) {
				oled_draw_pixel(x, y, ((x+shift)/scale) % 2 == ((y+shift)/scale) % 2);
			}
		}
		shift = (shift + 1) % scale;
		output_low(OUTPUT_LED_A);
		output_high(OUTPUT_LED_B);
		oled_flush();
		while (!status_is_set(STATUS_SPI_IDLE)) {}
		output_low(OUTPUT_LED_B);
		nop_loop(100000);
	}
}
