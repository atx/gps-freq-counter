
#include "regs.h"

#include "usb.h"
#include "usb_protocol.h"
#include "cmds.h"


static const struct usb_device_descriptor device_descriptor = {
	.bLength = 18,
	.bDescriptorType = USB_DESCRIPTOR_DEVICE,
	.bcdUSB = 0x200,
	.bDeviceClass = 0x00,
	.bDeviceSubClass = 0x00,
	.bDeviceProtocol = 0x00,
	.bMaxPacketSize0 = 0x08,
	.idVendor = 0x16c0,  // https://github.com/obdev/v-usb/blob/master/usbdrv/USB-IDs-for-free.txt
	.idProduct = 0x05dc,
	.bcdDevice = 0x0001,
	.iManufacturer = 0x01,
	.iProduct = 0x02,
	.iSerialNumber = 0x00,
	.bNumConfigurations = 0x01
};


static const struct usb_configuration_descriptor configuration_descriptor = {
	.bLength = 9,
	.bDescriptorType = USB_DESCRIPTOR_CONFIGURATION,
	.wTotalLength = 0,
	.bNumInterfaces = 0,  // This confuses the kernel somewhat, but seems to work fine
	.bConfigurationValue = 1,
	.iConfiguration = 0,
	.bmAttributes = 0x80,
	.bMaxPower = 200 / 2  // 200mA
};


static const struct usb_string_descriptor string_descriptor_language = {
	.bLength = 4,
	.bDescriptorType = USB_DESCRIPTOR_STRING,
	.data = {0x09, 0x04}  // EN_US
};


static const struct usb_string_descriptor string_descriptor_manufacturer = {
	.bLength = 29 * 2 + 2,
	.bDescriptorType = USB_DESCRIPTOR_STRING,
	.data = {
		'J','\0', 'o','\0', 's','\0', 'e','\0', 'f','\0', ' ','\0', 'G','\0',
		'a','\0', 'j','\0', 'd','\0', 'u','\0', 's','\0', 'e','\0', 'k','\0',
		' ','\0', '<','\0', 'a','\0', 't','\0', 'x','\0', '@','\0', 'a','\0',
		't','\0', 'x','\0', '.','\0', 'n','\0', 'a','\0', 'm','\0', 'e','\0',
		'>','\0',
	}
};


static const struct usb_string_descriptor string_descriptor_product = {
	.bLength = 21 * 2 + 2,
	.bDescriptorType = USB_DESCRIPTOR_STRING,
	.data = {
		'G','\0', 'P','\0', 'S','\0', ' ','\0', 'F','\0', 'r','\0', 'e','\0',
		'q','\0', 'u','\0', 'e','\0', 'n','\0', 'c','\0', 'y','\0', ' ','\0',
		'C','\0', 'o','\0', 'u','\0', 'n','\0', 't','\0', 'e','\0', 'r','\0'
	}
};


static const struct usb_string_descriptor *string_descriptors[] = {
	&string_descriptor_language,
	&string_descriptor_manufacturer,
	&string_descriptor_product,
};


// TODO: Move this to FPGA logic
// From https://github.com/xobs/grainuum/blob/master/grainuum-state.c#L58
#define CRC_POLY	0xa001
static uint16_t crc16_add(uint16_t crc, uint8_t c)
{
  uint8_t  i;

  for (i = 0; i < 8; i++) {
    if ((crc ^ c) & 1)
      crc = (crc >> 1) ^ CRC_POLY;
    else
      crc >>= 1;
    c >>= 1;
  }
  return crc;
}


static uint16_t crc16(const uint8_t *data, uint32_t size,
                      uint16_t init)
{

  while (size--)
    init = crc16_add(init, *data++);

  return init;
}


static void parse_setup_packet(struct usb_setup_packet *p)
{
	p->bmRequestType = USB_MEMORY[1];
	p->bRequest = USB_MEMORY[2];
	p->wValueL = USB_MEMORY[3];
	p->wValueH = USB_MEMORY[4];
	p->wIndex = BYTES_TO_SHORT(USB_MEMORY[6], USB_MEMORY[5]);
	p->wLength = BYTES_TO_SHORT(USB_MEMORY[8], USB_MEMORY[7]);
}


struct tx_buffer {
	const uint8_t *ptr;
	uint8_t remaining;
	bool finished;
};

struct tx_buffer tx_buffer = {
	.ptr = NULL,
	.remaining = 0,
	.finished = true,
};


static void tx_buffer_insert(const uint8_t *ptr, uint8_t len)
{
	tx_buffer.ptr = ptr;
	tx_buffer.remaining = len;
	tx_buffer.finished = false;
}

static bool tx_buffer_empty()
{
	return tx_buffer.remaining == 0;
}

static uint8_t tx_buffer_pop()
{
	tx_buffer.remaining--;
	return *tx_buffer.ptr++;
}

static void handle_get_descriptor(struct usb_setup_packet *p)
{
	uint8_t *ptr = NULL;
	uint8_t len = 0;
	// TODO: Make this an array
	switch(p->wValueH) {
	case USB_DESCRIPTOR_DEVICE:
		if (p->wValueL == 0) {
			// Hooray for casting stuff to another stuff!
			ptr = (uint8_t *)&device_descriptor;
			len = sizeof(device_descriptor);
		}
		break;
	case USB_DESCRIPTOR_CONFIGURATION:
		if (p->wValueL == 0) {
			ptr = (uint8_t *)&configuration_descriptor;
			len = sizeof(configuration_descriptor);
		}
		break;
	case USB_DESCRIPTOR_STRING:
		if (p->wValueL < ARRAY_SIZE(string_descriptors)) {
			const struct usb_string_descriptor *sd = string_descriptors[p->wValueL];
			ptr = (uint8_t *)sd;
			len = sd->bLength;
		}
		break;
	default:
		break;
	}

	tx_buffer_insert(ptr, MIN(len, p->wLength));
}


static void handle_command_in(struct usb_setup_packet *p)
{
	static const char *timestamp = __TIMESTAMP__;
	const uint8_t *ptr = NULL;
	uint8_t len = 0;
	switch (p->bRequest) {
	case CMD_GET_BUILD_DATE:
		ptr = (const uint8_t *)timestamp;
		len = strlen(timestamp);
		break;
	default:
		break;
	}

	tx_buffer_insert(ptr, MIN(len, p->wLength));
}


static void handle_command_out(struct usb_setup_packet *p)
{

}

// TODO: Move this to a struct...
static bool data_switch = false;

static uint8_t next_data() {
	data_switch = !data_switch;
	return data_switch ? USB_PID_DATA0 : USB_PID_DATA1;
}


void usb_rx_handle()
{
	uint8_t length = USB_REG & 0xf;
	if (length < 8) {
		// WTF?
		goto done;
	}
	uint8_t pid = USB_MEMORY[0];
	if (pid != USB_PID_DATA0 && pid != USB_PID_DATA1) {
		// WTF v2?
		goto done;
	}
	data_switch = pid == USB_PID_DATA0;

	struct usb_setup_packet p;
	parse_setup_packet(&p);
	if (p.bmRequestType == (USB_BM_REQUEST_TYPE_DIRECTION | USB_BM_REQUEST_TYPE_TYPE_STANDARD) &&
			p.bRequest == USB_BREQUEST_GET_DESCRIPTOR) {
		handle_get_descriptor(&p);
	} else if (p.bmRequestType == USB_BM_REQUEST_TYPE_TYPE_STANDARD &&
			(p.bRequest == USB_BREQUEST_SET_ADDRESS || p.bRequest == USB_BREQUEST_SET_CONFIGURATION)) {
		tx_buffer.finished = false;  // Trigger zero-length response
	} else if (p.bmRequestType == (USB_BM_REQUEST_TYPE_DIRECTION | USB_BM_REQUEST_TYPE_TYPE_VENDOR)) {
		handle_command_in(&p);
	} else if (p.bmRequestType == USB_BM_REQUEST_TYPE_TYPE_VENDOR) {
		handle_command_out(&p);
	} else {
		// Wat?
		tx_buffer.finished = true;
	}

done:
	USB_REG = USB_REG_ACK;
}


void usb_tx_handle()
{
	if (tx_buffer.finished) {
		return;
	}

	uint8_t len = 0;
	USB_MEMORY[len++] = next_data();
	uint16_t crc = 0xffff;
	while (!tx_buffer_empty() && len < 9) {
		uint8_t v = tx_buffer_pop();
		USB_MEMORY[len++] = v;
		crc = crc16_add(crc, v);
	}
	crc = ~crc;
	USB_MEMORY[len++] = SELECT_BYTE(crc, 0);
	USB_MEMORY[len++] = SELECT_BYTE(crc, 1);
	if (tx_buffer_empty()) {
		tx_buffer.finished = true;
	}
	USB_REG = len;
}
