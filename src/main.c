
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

#include "oled.h"
#include "pps.h"
#include "regs.h"
#include "ublox.h"
#include "ui.h"
#include "usb.h"
#include "utils.h"


#define TICK_EVERY_MS		100

struct bit_handler {
	uint32_t mask;
	void (*fn)(void);
};

static struct bit_handler ack_handlers[] = {
	{ ACK_BUTTON_DOWN, ui_on_key_down },
	{ ACK_BUTTON_UP, ui_on_key_up } ,
	{ ACK_UART_RXFULL, uart_process_rx },
	{ ACK_PPS, pps_handler },
};

static struct bit_handler status_handlers[] = {
	{ STATUS_UART_TXEMPTY, uart_process_tx },
	{ STATUS_USB_RXDONE, usb_rx_handle },
	{ STATUS_USB_TXEMPTY, usb_tx_handle },
};

static void call_handlers(uint32_t val, struct bit_handler *handlers, size_t length)
{
	for (size_t i = 0; i < length; i++) {
		if (val & handlers[i].mask) {
			handlers[i].fn();
		}
	}
}


void main()
{
	oled_init();

	timems_t next_tick = time_ms();
	while (true) {

		if (time_ms() > 1000) {
			output_high(OUTPUT_USB);
		}

		uint32_t ackr = ack_status();
		if (ackr) {
			output_high(OUTPUT_LED_A);
			call_handlers(ackr, ack_handlers, ARRAY_SIZE(ack_handlers));
			ack_write(ackr);
		}

		uint32_t stat = STATUS_REG;
		if (stat) {
			call_handlers(stat, status_handlers, ARRAY_SIZE(status_handlers));
		}

		if (time_ms() < next_tick) {
			output_low(OUTPUT_LED_A);
			continue;
		}
		output_high(OUTPUT_LED_A);

		next_tick = time_ms() + TICK_EVERY_MS;

		ui_on_frame();
		oled_flush();

		output_low(OUTPUT_LED_A);
	}
}
