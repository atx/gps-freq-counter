
#pragma once

#include <stdint.h>

#define SPI_BUFFER_LENGTH (260*4)

void spi_buff_write8(uint32_t offset, uint8_t value);
void spi_buff_write_chunk(uint32_t offset, uint8_t *data, uint32_t length);
void *spi_get_buffer_pointer(uint32_t offset);
void spi_start(uint32_t offset, uint16_t nbytes);
