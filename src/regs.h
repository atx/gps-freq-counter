
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
