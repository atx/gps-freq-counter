#! /usr/bin/env python3

import collections
import dateutil.parser
import enum
import math
import pathlib
import struct
import time
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


def ansi_escape(code, *args):
    return "\x1b[" + ";".join(map(str, args)) + code


def do_config(dev, args):
    print("Firmware build date '{}'".format(dev.build_date.isoformat(" ")))
    if args.input:
        print("Configuring input to '{}'".format(args.input))
        dev.select_input(getattr(Device.Input, args.input.upper()))


def do_raw(dev, args):
    with args.output.open("w") as fout:
        timestamp, value = None, None
        while True:
            new_timestamp, new_value = dev.fetch_measurement()
            gps_timestamp, gps_has_fix, gps_n_sats = dev.fetch_gps_status()
            if new_timestamp != timestamp:
                print("{: 9d}     {: 9d}     {: 2d} {}"
                      .format(new_timestamp, new_value, gps_n_sats, gps_has_fix),
                      flush=True, file=fout)
            else:
                assert value == new_value, "What? Same timestamp but different values..."
            timestamp, value = new_timestamp, new_value
            time.sleep(0.2)


def do_measure(dev, args):
    total_dropped = 0
    # This array will grow without bounds, intentional for now
    measurements = []
    print(ansi_escape("J", 2))  # Clear screen
    with args.file.open("w") as fout:
        timestamp = None
        new_timestamp = None
        while True:
            time.sleep(0.2)
            timestamp = new_timestamp
            new_timestamp, value = dev.fetch_measurement()
            if timestamp is None or new_timestamp == timestamp:
                continue

            fout.write("raw {} {}\n".format(new_timestamp, value))

            if abs(new_timestamp - timestamp) > 1050:  # Magical constant!
                total_dropped += 1
                continue
            measurements.append(value)
            avg = sum(measurements) / len(measurements)

            ppm = None
            if args.target:
                ppm = (avg - args.target) / args.target * 1e6
            elif len(measurements) > 5:
                # 100kHz rouding
                args.target = round(avg, -5)

            fout.write("processed {} {} {}\n".format(new_timestamp, value, ppm))

            lines = [
                "Last value:               {} Hz".format(measurements[-1]),
                "Average value:            {:.6f} Hz".format(avg),
                "Count (drop):             {: 4d} ({})".format(len(measurements), total_dropped),
            ]
            if ppm is not None:
                lines.extend([
                    "Error:                      {:.5f} ppm".format(ppm),
                    "Target:                   {} Hz".format(args.target),
                ])
            max_length = max(map(len, lines))
            lines = ["| " + l.ljust(max_length) + " |" for l in lines]
            borders = "-" * (max_length + 2)
            lines = ["/" + borders + "\\"] + lines + ["\\" + borders + "/"]

            out = ansi_escape("H", 1, 1) + "\n".join(lines)
            print(out)
            fout.flush()


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="mode")
    subparsers.required = True

    parser_config = subparsers.add_parser("config")
    parser_config.add_argument("-i", "--input", choices=["internal", "external"])

    parser_raw = subparsers.add_parser("raw")
    parser_raw.add_argument(
        "-o", "--output",
        type=pathlib.Path,
        default=pathlib.Path("/dev/stdout")
    )

    parser_measure = subparsers.add_parser("measure")
    parser_measure.add_argument(
        "-f", "--file",
        type=pathlib.Path,
        default=pathlib.Path("/dev/null")
    )
    parser_measure.add_argument(
        "-t", "--target",
        type=float
    )

    args = parser.parse_args()

    dev = Device()

    {
        "config": do_config,
        "raw": do_raw,
        "measure": do_measure,
    }[args.mode](dev, args)
