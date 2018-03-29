#! /usr/bin/env python3

import argparse

# Converts raw objdump binary file to format readable by $readmemh() in Verilog

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("output")
    args = parser.parse_args()

    with open(args.input, "rb") as fin, open(args.output, "w") as fout:
        while True:
            d = fin.read(4)
            if len(d) == 0:
                break
            word = 0x00000000
            for i, c in enumerate(d):
                word |= (c << (i * 8))
            fout.write("%08x " % word)
