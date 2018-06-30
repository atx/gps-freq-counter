
#pragma once

#define USB_MEMORY		((volatile uint32_t *)0x32000000)

void usb_rx_handle();
void usb_tx_handle();
