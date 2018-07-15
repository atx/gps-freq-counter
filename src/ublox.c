
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>

#include "regs.h"
#include "ui.h"
#include "utils.h"

#include "ublox.h"

#if __BYTE_ORDER__  != __ORDER_LITTLE_ENDIAN__
#error "This code works only on little endian machines"
#endif


struct fletcher_state {
	uint8_t ck_a;
	uint8_t ck_b;
};

#define FLETCHER_INIT { .ck_a = 0, .ck_b = 0 }

void fletcher_next(struct fletcher_state *f, uint8_t b)
{
	f->ck_a += b;
	f->ck_b += f->ck_a;
}


#define UBX_ACK_ACK			0x0105
#define UBX_NAV_STATUS		0x0301
#define UBX_NAV_SVINFO		0x3001
#define UBX_TIM_TP			0x010d

struct ublox_nav_status {
	uint32_t iTOW;
	uint8_t gpsFix;
	uint8_t flags;
	uint8_t fixStat;
	uint8_t flags2;
	uint32_t ttff;
	uint32_t msss;
} __attribute__((packed));

struct ublox_nav_svinfo {
	uint32_t iTOW;
	uint8_t numCh;
	uint8_t globalFlags;
	uint16_t r1;
	struct {
		uint8_t chn;
		uint8_t svid;
		uint8_t flags;
		uint8_t quality;
		uint8_t cno;
		int8_t elev;
		int16_t azim;
		int32_t prRes;
	} channels[];
} __attribute__((packed));


struct ublox_header {
	uint16_t msg_id;
	uint16_t length;
	union {
		struct ublox_nav_status nav_status;
		struct ublox_nav_svinfo svinfo;
	};
} __attribute__((packed));


struct ublox_state ublox_state = {
	.n_sats = 0,
	.has_fix = false,
	.last_update = 0,
};


__attribute__((weak))
void ublox_gps_state_change_handler(void) {}


static void dispatch_msg(const struct ublox_header *ubx)
{
	switch (ubx->msg_id) {
	case UBX_NAV_STATUS:
		ublox_state.has_fix = ubx->nav_status.gpsFix == 0x02 || ubx->nav_status.gpsFix == 0x03;
		ublox_gps_state_change_handler();
		break;
	case UBX_NAV_SVINFO: {
		unsigned int n_sats = 0;
		for (unsigned int i = 0; i < ubx->svinfo.numCh; i++) {
			if (ubx->svinfo.channels[i].quality >= 2) {
				n_sats++;
			}
		}
		ublox_state.n_sats = n_sats;
		ublox_state.last_update = time_ms();
		ublox_gps_state_change_handler();
		break;
	}
	case UBX_TIM_TP:
		// TODO;
		break;
	case UBX_ACK_ACK:
		break;
	default:
		break;
	}

}


// This will eventually overflow, but we want to use it only on startup anyway
static unsigned int rx_byte_counter = 0;

void uart_process_rx()
{
	static uint8_t rxbuff[256];
	static unsigned int rxptr = 0;
	const struct ublox_header *ubx = (struct ublox_header *)&rxbuff;
	static struct fletcher_state cksum;
	static uint8_t prev_c = 0x00;
	static bool receiving = false;

	rx_byte_counter++;
	char c = uart_read();
	if (rxptr >= ARRAY_SIZE(rxbuff)) {
		// This should never happen, but just in case...
		rxptr = 0;
	}

	rxbuff[rxptr++] = c;

	if (!receiving) {
		rxptr = 0;
		if (prev_c == 0xb5 && c == 0x62) {
			cksum.ck_a = 0;
			cksum.ck_b = 0;
			receiving = true;
			output_high(OUTPUT_LED_B);
		}
	} else {
		if (rxptr < 4 || (rxptr - 4) <= ubx->length) {
			fletcher_next(&cksum, c);
		}
		if (rxptr >= 6 && (rxptr - 6) == ubx->length) {
			if (cksum.ck_a != rxbuff[rxptr-2] || cksum.ck_b != rxbuff[rxptr-1]) {
				// Welp
			} else {
				dispatch_msg(ubx);
			}

			receiving = false;
			rxptr = 0;
			output_low(OUTPUT_LED_B);
		}
	}

	prev_c = c;
}


static const uint8_t init_messages[] = {
	// Enable NAV-STATUS
	0xb5,0x62, 0x06,0x01, 0x03,0x00, 0x01,0x03,0x01, 0x0f,0x49,
	// Enable NAV-SVINFO
	0xb5,0x62, 0x06,0x01, 0x03,0x00, 0x01,0x30,0x01, 0x3c,0xa3,
	// Enable TIM-TP
	0xb5,0x62, 0x06,0x01, 0x03,0x00, 0x0d,0x01,0x01, 0x19,0x69
};


void uart_process_tx()
{
	static uint8_t cnt = 0;

	if (rx_byte_counter < 100) {
		// The ublox module hasn't been properly initilized yet,
		// let's just wait for a bunch of the $GP* messages
		return;
	}

	if (cnt == ARRAY_SIZE(init_messages)) {
		return;
	}

	uart_write(init_messages[cnt++]);
}
