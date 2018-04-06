
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>

#include "ui.h"
#include "utils.h"


static bool verify_checksum(const char *buff)
{
	// NMEA checksum is XOR of bytes between $ and *
	// The expected checksum is then in hexadecimal at the end
	buff++;
	uint8_t ck = 0;
	while (*buff != '\0' && *buff != '*') {
		ck ^= *buff++;
	}

	if (*buff == '*') {
		// TODO: It might be a good idea to get a specialized integer parsing
		// functions instead of the whole strtol beast
		if (buff[1] == '\0' || buff[2] == '\0') {
			return false;
		}
		uint8_t exp = strtol(buff + 1, NULL, 16);
		return exp == ck;
	}

	return false;
}


void uart_rx_line_callback(char *buff, size_t length)
{
	// Note that the buffer is intentionally not const - it's going to get
	// dropped by the uart code anyway, so we can at least use it for strtok

	if (!str_startswith(buff, "$GPGGA") || !verify_checksum(buff)) {
		return;
	}

	char *saveptr;
	strtok_r(buff, ",", &saveptr);
	char *tokens[30];
	unsigned int i = 0;
	while (i < ARRAY_SIZE(tokens) && (tokens[i++] = strtok_r(NULL, ",", &saveptr)) != NULL) {}

	if (i < 7) {  // Corrupted message
		return;
	}

	bool has_fix = tokens[5][0] == '1';
	unsigned int n_sats = atoi(tokens[6]);
	ui_on_gps_update(has_fix, n_sats);
}
