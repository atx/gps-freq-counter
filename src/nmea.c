
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>

#include "ui.h"
#include "utils.h"

void uart_rx_line_callback(char *buff, size_t length)
{
	// Note that the buffer is intentionally not const - it's going to get
	// dropped by the uart code anyway, so we can at least use it for strtok
	char *saveptr;
	char *type = strtok_r(buff, ",", &saveptr);
	if (strcmp(type, "$GPGGA")) {
		return;
	}

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
