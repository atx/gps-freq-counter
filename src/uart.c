
#include <stdlib.h>
#include <stdint.h>

#include "utils.h"

#include "uart.h"

#define UART_REG			(*((volatile uint32_t *)0x31000010l))

extern void uart_rx_line_callback(char *buff, size_t length);

#define BUFFER_SIZE 512

struct uart_buffer {
	size_t ptr;
	char data[BUFFER_SIZE];
};

static struct uart_buffer rx_buffer = {
	.ptr = 0,
};

void uart_process_rx()
{
	char c = SELECT_BYTE(UART_REG, 1);
	if (rx_buffer.ptr == BUFFER_SIZE - 1) {
		// Just drop the entire buffer so far. This should not hopefully ever happen
		// TODO: Incomplete lines should be dropped completely
		rx_buffer.ptr = 0;
	}
	if (c == '\n') {
		rx_buffer.data[rx_buffer.ptr] = '\0';
		if (rx_buffer.ptr != 0) {
			uart_rx_line_callback(rx_buffer.data, rx_buffer.ptr);
		}
		rx_buffer.ptr = 0;
	} else {
		rx_buffer.data[rx_buffer.ptr++] = c;
	}
}

void uart_process_tx()
{
	// TODO
}
