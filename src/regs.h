
#pragma once

#include <stdint.h>
#include <stdbool.h>

#include "utils.h"

#define STATUS_REG			(*((volatile uint32_t *)0x31000000l))
#define STATUS_SPI_IDLE		BIT(0)

static inline bool status_is_set(uint32_t mask)
{
	return (STATUS_REG & mask) == mask;
}

#define OUTPUT_REG			(*((volatile uint32_t *)0x31000004l))
#define OUTPUT_OLED_DC		BIT(0)
#define OUTPUT_OLED_RST		BIT(1)
#define OUTPUT_LED_A		BIT(2)
#define OUTPUT_LED_B		BIT(3)

static inline void output_high(uint32_t mask)
{
	OUTPUT_REG |= mask;
}

static inline void output_low(uint32_t mask)
{
	OUTPUT_REG &= ~mask;
}

#define MSTIMER_REG			(*((volatile uint32_t *)0x31000008l))

static inline uint32_t time_ms()
{
	return MSTIMER_REG;
}

#define ACK_REG				(*((volatile uint32_t *)0x3100000cl))
#define ACK_BUTTON_DOWN		BIT(0)
#define ACK_BUTTON_UP		BIT(1)
#define ACK_UART_RXFULL		BIT(2)
#define ACK_UART_TXEMPTY	BIT(3)

static inline uint32_t ack_status()
{
	return ACK_REG;
}

static inline void ack_write(uint32_t val)
{
	ACK_REG = val;
}
