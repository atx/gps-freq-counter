
#include <stdlib.h>
#include <stdint.h>

#include "spi.h"
#include "utils.h"

uint8_t to_send[] = {
	0xaa, 0x1d, 0xbe, 0xef, 0xaa, 0x10, 0x41
};

void main()
{
	spi_buff_write_chunk(0xc, to_send, ARRAY_SIZE(to_send));
	spi_start(0xc, ARRAY_SIZE(to_send));
}
