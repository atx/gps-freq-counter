#! /usr/bin/env python3

import collections
import dateutil.parser
import struct
import usb.core
import usb.util

Command = collections.namedtuple("Command", ["cmd", "length"])


class Command:
    GET_BUILD_DATE = Command(0x01, 24)
    GET_MEASUREMENT = Command(0x02, 8)


class Device:

    def __init__(self):
        self.dev = usb.core.find(product="GPS Frequency Counter")
        self._build_date = None
        if self.dev is None:
            raise IOError("Could not find the USB device")

    def command_in(self, cmd, arg1=0, arg2=0):
        return self.dev.ctrl_transfer(
            bmRequestType=(usb.util.CTRL_IN | usb.util.CTRL_TYPE_VENDOR | usb.util.CTRL_RECIPIENT_DEVICE),
            bRequest=cmd.cmd,
            wValue=arg1,
            wIndex=arg2,
            data_or_wLength=cmd.length
        )

    @property
    def build_date(self):
        if self._build_date:
            return self._build_date
        s = self.command_in(Command.GET_BUILD_DATE).tobytes()
        self._build_date = dateutil.parser.parse(s)
        return self._build_date

    def fetch_measurement(self):
        raw = self.command_in(Command.GET_MEASUREMENT)
        timestamp, value = struct.unpack("<II", raw)
        return timestamp, value


if __name__ == "__main__":
    import sys
    import time
    # TODO: Make this into a proper script+package
    gfc = Device()
    print("Firmware build date '{}'".format(gfc.build_date.isoformat(" ")),
          file=sys.stderr)
    timestamp, value = None, None
    while True:
        new_timestamp, new_value = gfc.fetch_measurement()
        if new_timestamp != timestamp:
            print("{: 9d}     {: 9d}".format(new_timestamp, new_value))
        else:
            assert value == new_value, "What? Same timestamp but different values..."
        timestamp, value = new_timestamp, new_value
        time.sleep(0.2)
