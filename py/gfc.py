#! /usr/bin/env python3

import collections
import dateutil.parser
import enum
import struct
import usb.core
import usb.util

Command = collections.namedtuple("Command", ["cmd", "length"])


class Command:
    GET_BUILD_DATE = Command(0x01, 24)
    GET_MEASUREMENT = Command(0x02, 8)
    GET_GPS_INFO = Command(0x03, 5)
    SET_INPUT = Command(0x04, 0)


class Device:

    class Input(enum.IntEnum):
        INTERNAL = 0
        EXTERNAL = 1

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

    def fetch_gps_status(self):
        raw = self.command_in(Command.GET_GPS_INFO)
        timestamp, value = struct.unpack("<IB", raw)
        mask = 1 << 7
        has_fix = bool(value & mask)
        n_sats = value & ~mask
        return timestamp, has_fix, n_sats

    def fetch_measurement(self):
        raw = self.command_in(Command.GET_MEASUREMENT)
        timestamp, value = struct.unpack("<II", raw)
        return timestamp, value

    def select_input(self, select):
        return self.command_in(Command.SET_INPUT, arg1=int(select))


if __name__ == "__main__":
    import argparse
    import sys
    import time
    # TODO: Make this into a proper script+package
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--input", choices=["internal", "external"])
    args = parser.parse_args()

    gfc = Device()
    print("Firmware build date '{}'".format(gfc.build_date.isoformat(" ")),
          file=sys.stderr)

    if args.input:
        gfc.select_input(getattr(Device.Input, args.input.upper()))

    timestamp, value = None, None
    while True:
        new_timestamp, new_value = gfc.fetch_measurement()
        gps_timestamp, gps_has_fix, gps_n_sats = gfc.fetch_gps_status()
        if new_timestamp != timestamp:
            print("{: 9d}     {: 9d}     {: 2d} {}"
                  .format(new_timestamp, new_value, gps_n_sats, gps_has_fix), flush=True)
        else:
            assert value == new_value, "What? Same timestamp but different values..."
        timestamp, value = new_timestamp, new_value
        time.sleep(0.2)
