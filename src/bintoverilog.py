#! /usr/bin/env python3

import argparse
import itertools
import math
import os

# Converts raw objdump binary file to format readable by $readmemh() in Verilog

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("output")
    parser.add_argument(
        "-f", "--format",
        choices=["verilog", "mif"],
        default="verilog"
    )
    args = parser.parse_args()

    with open(args.input, "rb") as fin, open(args.output, "w") as fout:
        if args.format == "mif":
            size = os.path.getsize(args.input)
            fout.write("\n".join(
                ["DEPTH = {};".format(math.ceil(size / 4)),
                 "WIDTH = 32;",
                 "DATA_RADIX = HEX;",
                 "CONTENT",
                 "BEGIN", "", ""]
            ))

        for addr in itertools.count():
            d = fin.read(4)
            if len(d) == 0:
                break
            if len(d) < 4:
                d += b"\x00" * (4 - len(d))
            word = 0x00000000
            for i, c in enumerate(d):
                word |= (c << (i * 8))

            if args.format == "mif":
                fout.write("{:08x}: {:08x};\n".format(addr, word))
            else:
                fout.write("%08x " % word)

        if args.format == "mif":
            fout.write("\nEND;\n")
