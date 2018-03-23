
#pragma once

#include <stdint.h>

void spi_buff_write8(uint32_t offset, uint8_t value);
void spi_buff_write_chunk(uint32_t offset, uint8_t *data, uint32_t length);
void spi_start(uint32_t offset, uint16_t nbytes);
