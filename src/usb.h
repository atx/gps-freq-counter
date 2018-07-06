
#pragma once

#include <stdbool.h>

#define USB_MEMORY		((volatile uint32_t *)0x32000000)

bool usb_is_connected();
void usb_rx_handle();
void usb_tx_handle();
