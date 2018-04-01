
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

#include "regs.h"
#include "uart.h"
#include "utils.h"
#include "oled.h"
#include "ui.h"


#define TICK_EVERY		100

void main()
{
	oled_init();
	ui_init();
	uint32_t next_tick = time_ms();
	while (true) {
		uint32_t ackr = ack_status();
		if (ackr & ACK_BUTTON_DOWN) {
			ui_on_key_down();
		}
		if (ackr & ACK_BUTTON_UP) {
			ui_on_key_up();
		}
		if (ackr & ACK_UART_RXFULL) {
			uart_process_rx();
		}
		if (ackr & ACK_UART_TXEMPTY) {
			uart_process_tx();
		}
		ack_write(ackr);

		if (time_ms() < next_tick) {
			continue;
		}

		output_high(OUTPUT_LED_A);
		next_tick = time_ms() + TICK_EVERY;

		ui_on_frame();
		oled_flush();

		output_low(OUTPUT_LED_A);
	}
}
