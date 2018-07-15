
#include "cmds.h"
#include "pps.h"
#include "regs.h"
#include "ublox.h"
#include "ui.h"

#include "usb.h"
#include "usb_protocol.h"


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
	.wTotalLength = 9 + 9,
	.bNumInterfaces = 1,
	.bConfigurationValue = 1,
	.iConfiguration = 0,
	.bmAttributes = 0x80,
	.bMaxPower = 200 / 2,  // 200mA
	.interface = {
		.bLength = 9,
		.bDescriptorType = USB_DESCRIPTOR_INTERFACE,
		.bInterfaceNumber = 0,
		.bAlternateSetting = 0,
		.bNumEndpoints = 0,
		.bInterfaceClass = 0xff,  // Vendor class
		.bInterfaceSubclass = 0x00,
		.bInterfaceProtocol = 0x00,
		.iInterface = 0
	}
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


static void parse_setup_packet(struct usb_setup_packet *p)
{
	p->bmRequestType = USB_MEMORY[1];
	p->bRequest = USB_MEMORY[2];
	p->wValueL = USB_MEMORY[3];
	p->wValueH = USB_MEMORY[4];
	p->wIndex = BYTES_TO_SHORT(USB_MEMORY[6], USB_MEMORY[5]);
	p->wLength = BYTES_TO_SHORT(USB_MEMORY[8], USB_MEMORY[7]);
}


struct usb_state {
	bool data_pid_switch;
	bool initialized;
	struct {
		const uint8_t *ptr;
		uint8_t remaining;
		bool finished;
	} tx_buffer;
	uint8_t shared_buffer[64];
};


static struct usb_state usb_state = {
	.initialized = false,
	.tx_buffer = {
		.ptr = NULL,
		.remaining = 0,
		.finished = true,
	},
};


static void tx_buffer_insert(const uint8_t *ptr, uint8_t len)
{
	usb_state.tx_buffer.ptr = ptr;
	usb_state.tx_buffer.remaining = len;
	usb_state.tx_buffer.finished = false;
}


static bool tx_buffer_empty()
{
	return usb_state.tx_buffer.remaining == 0;
}


static uint8_t tx_buffer_pop()
{
	usb_state.tx_buffer.remaining--;
	return *usb_state.tx_buffer.ptr++;
}


static bool tx_buffer_finished()
{
	return usb_state.tx_buffer.finished;
}


static void tx_buffer_finish()
{
	usb_state.tx_buffer.finished = true;
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
			len = configuration_descriptor.wTotalLength;
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


struct command_handler {
	enum cmd cmd;
	const uint8_t *(*fn)(struct usb_setup_packet *, uint8_t *);
};


const uint8_t *command_get_build_date(struct usb_setup_packet *setup, uint8_t *len)
{
	static const char *timestamp = __TIMESTAMP__;
	*len = strlen(timestamp);
	return (const uint8_t *)timestamp;
}


const uint8_t *command_get_measurement(struct usb_setup_packet *setup, uint8_t *len)
{
	uint8_t *ptr = usb_state.shared_buffer;
	uint8_t *p = ptr;
	p = serialize_uint32(p, pps_state.timestamp);
	p = serialize_uint32(p, pps_state.value);
	*len = 8;
	return ptr;
}


const uint8_t *command_get_gps_info(struct usb_setup_packet *setup, uint8_t *len)
{
	uint8_t *ptr = usb_state.shared_buffer;
	uint8_t *p = ptr;
	p = serialize_uint32(p, ublox_state.last_update);
	*p = ublox_state.n_sats | (ublox_state.has_fix ? BIT(7) : 0);
	*len = 5;
	return ptr;
}


const uint8_t *command_set_input(struct usb_setup_packet *setup, uint8_t *len)
{
	ui_set_input(setup->wValueL == 0 ? PPS_INPUT_INTERNAL : PPS_INPUT_EXTERNAL);
	*len = 0;
	return NULL;
}


static struct command_handler command_handlers[] = {
	{ CMD_GET_BUILD_DATE, command_get_build_date },
	{ CMD_GET_MEASUREMENT, command_get_measurement },
	{ CMD_GET_GPS_INFO, command_get_gps_info },
	{ CMD_SET_INPUT, command_set_input },
};


static void handle_command_in(struct usb_setup_packet *p)
{
	const uint8_t *ptr = NULL;
	uint8_t len = 0;

	for (size_t i = 0; i < ARRAY_SIZE(command_handlers); i++) {
		if (command_handlers[i].cmd == p->bRequest) {
			ptr = command_handlers[i].fn(p, &len);
			break;
		}
	}

	tx_buffer_insert(ptr, MIN(len, p->wLength));
}


static void handle_command_out(struct usb_setup_packet *p)
{

}

static uint8_t next_data_pid() {
	usb_state.data_pid_switch = !usb_state.data_pid_switch;
	return usb_state.data_pid_switch ? USB_PID_DATA0 : USB_PID_DATA1;
}


bool usb_is_connected()
{
	return usb_state.initialized;
}


void usb_rx_handle()
{
	uint8_t length = USB_REG & USB_REG_LENGTH;
	if (length < 8) {
		// WTF?
		goto done;
	}
	uint8_t pid = USB_MEMORY[0];
	if (pid != USB_PID_DATA0 && pid != USB_PID_DATA1) {
		// WTF v2?
		goto done;
	}
	usb_state.data_pid_switch = pid == USB_PID_DATA0;

	struct usb_setup_packet p;
	parse_setup_packet(&p);
	if (p.bmRequestType == (USB_BM_REQUEST_TYPE_DIRECTION | USB_BM_REQUEST_TYPE_TYPE_STANDARD) &&
			p.bRequest == USB_BREQUEST_GET_DESCRIPTOR) {
		handle_get_descriptor(&p);
	} else if (p.bmRequestType == USB_BM_REQUEST_TYPE_TYPE_STANDARD &&
			(p.bRequest == USB_BREQUEST_SET_ADDRESS || p.bRequest == USB_BREQUEST_SET_CONFIGURATION)) {
		usb_state.initialized = true;
		tx_buffer_insert(NULL, 0);  // Trigger zero-length response
	} else if (p.bmRequestType == (USB_BM_REQUEST_TYPE_DIRECTION | USB_BM_REQUEST_TYPE_TYPE_VENDOR)) {
		handle_command_in(&p);
	} else if (p.bmRequestType == USB_BM_REQUEST_TYPE_TYPE_VENDOR) {
		handle_command_out(&p);
	} else {
		// Wat?
		usb_state.tx_buffer.finished = true;
	}

done:
	USB_REG = USB_REG_ACK;
}


void usb_tx_handle()
{
	if (tx_buffer_finished()) {
		return;
	}

	uint8_t len = 0;
	USB_MEMORY[len++] = next_data_pid();
	while (!tx_buffer_empty() && len < 9) {
		uint8_t v = tx_buffer_pop();
		USB_MEMORY[len++] = v;
	}
	if (tx_buffer_empty()) {
		tx_buffer_finish();
	}
	USB_REG = len;
}
