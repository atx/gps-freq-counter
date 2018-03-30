
#include "regs.h"

#include "spi.h"

#define SPI_CONTROL (*((volatile uint32_t *)0x30000000))
static volatile uint8_t *spi_buffer = (void *)0x30000004;

void spi_buff_write8(uint32_t offset, uint8_t value)
{
	spi_buffer[offset] = value;
}

void spi_buff_write_chunk(uint32_t offset, uint8_t *data, uint32_t length)
{
	for (uint32_t i = 0; i < length; i++) {
		spi_buff_write8(offset + i, data[i]);
	}
}

volatile void *spi_get_buffer_pointer(uint32_t offset)
{
	return &spi_buffer[offset];
}

void spi_start(uint32_t offset, uint16_t nbytes)
{
	while (!status_is_set(STATUS_SPI_IDLE)) {}
	// Offset is in 4-byte chunks, buyers beware
	SPI_CONTROL = (nbytes << 8) | ((offset / 4) & 0xff);
}
