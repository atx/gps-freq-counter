
#pragma once

#include <stdint.h>

#include "utils.h"

// For reference:
// https://github.com/xobs/grainuum/blob/master/grainuum.h
// Note that we can't just directly map structs on our usb memory, as it is
// only byte-addressable

enum usb_pids {
	USB_PID_RESERVED = 0xf0,
	USB_PID_OUT = 0xe1,
	USB_PID_ACK = 0xd2,
	USB_PID_DATA0 = 0xc3,
	USB_PID_PING = 0xb4,
	USB_PID_SOF = 0xa5,
	USB_PID_NYET = 0x96,
	USB_PID_DATA2 = 0x87,
	USB_PID_SPLIT = 0x78,
	USB_PID_IN = 0x69,
	USB_PID_NAK = 0x5a,
	USB_PID_DATA1 = 0x4b,
	USB_PID_ERR = 0x3c,
	USB_PID_SETUP = 0x2d,
	USB_PID_STALL = 0x1e,
	USB_PID_MDATA = 0x0f,
};


#define USB_BM_REQUEST_TYPE_DIRECTION		BIT(7)
#define USB_BM_REQUEST_TYPE_TYPE_MASK		(BIT(6) | BIT(5))
#define USB_BM_REQUEST_TYPE_TYPE_STANDARD	(0 << 5)
#define USB_BM_REQUEST_TYPE_TYPE_CLASS		(1 << 5)
#define USB_BM_REQUEST_TYPE_TYPE_VENDOR		(2 << 5)
#define USB_BM_REQUEST_TYPE_TYPE_RESERVED	(3 << 5)


enum usb_brequest {
	USB_BREQUEST_GET_STATUS = 0,
	USB_BREQUEST_CLEAR_FEATURE = 1,
	USB_BREQUEST_SET_FEATURE = 3,
	USB_BREQUEST_SET_ADDRESS = 5,
	USB_BREQUEST_GET_DESCRIPTOR = 6,
	USB_BREQUEST_SET_DESCRIPTOR = 7,
	USB_BREQUEST_GET_CONFIGURATION = 8,
	USB_BREQUEST_SET_CONFIGURATION = 9,
	USB_BREQUEST_GET_INTERFACE = 10,
	USB_BREQUEST_SET_INTERFACE = 11,
	USB_BREQUEST_SYNC_FRAME = 12,
};


enum usb_descriptor {
	USB_DESCRIPTOR_DEVICE = 0x01,
	USB_DESCRIPTOR_CONFIGURATION = 0x02,
	USB_DESCRIPTOR_STRING = 0x03,
	USB_DESCRIPTOR_INTERFACE = 0x04,
	USB_DESCRIPTOR_ENDPOINT = 0x05,
	USB_DESCRIPTOR_DEVICE_QUALIFIER = 0x06,
	USB_DESCRIPTOR_OTHER_SPEED_CONFIGURATION = 0x07,
	USB_DESCRIPTOR_INTERFACE_POWER = 0x08,
};


struct usb_setup_packet {
  uint8_t bmRequestType;
  uint8_t bRequest;
  union {
    uint16_t wValue;
    struct {
      uint8_t wValueL;
      uint8_t wValueH;
    };
  };
  uint16_t wIndex;
  uint16_t wLength;
} __attribute__((packed, aligned(4)));


struct usb_device_descriptor {
	uint8_t  bLength;
	uint8_t  bDescriptorType;
	uint16_t bcdUSB;
	uint8_t  bDeviceClass;
	uint8_t  bDeviceSubClass;
	uint8_t  bDeviceProtocol;
	uint8_t  bMaxPacketSize0;
	uint16_t idVendor;
	uint16_t idProduct;
	uint16_t bcdDevice;
	uint8_t  iManufacturer;
	uint8_t  iProduct;
	uint8_t  iSerialNumber;
	uint8_t  bNumConfigurations;
} __attribute__((packed));


struct usb_configuration_descriptor {
  uint8_t  bLength;
  uint8_t  bDescriptorType;
  uint16_t wTotalLength;
  uint8_t  bNumInterfaces;
  uint8_t  bConfigurationValue;
  uint8_t  iConfiguration;
  uint8_t  bmAttributes;
  uint8_t  bMaxPower;
  uint8_t  data[];
} __attribute__((packed));


struct usb_string_descriptor {
	uint8_t bLength;
	uint8_t bDescriptorType;
	uint8_t data[];
} __attribute__((packed));
