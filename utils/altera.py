#! /usr/bin/env python3

import csv
from eeschema import *

lib = SchemaLib()
com = Component("10M08SAE144", nparts=11)
lib.add_component(com)

partbanks = {
    "PWR": 1, "JTAG": 2, "ADC": 3,
    "1A": 4, "1B": 4, "2": 5, "3": 6, "4": 7, "5": 8, "6": 9, "7": 10,  "8": 11
}
ys = [0] * max(partbanks.values())

with open("10m08sa.txt") as fin:
    reader = csv.DictReader(fin, delimiter="\t")
    for row in reader:
        print(row)
        bank = row["Bank Number"]
        name = row["Pin Name/Function"]
        pin = row["E144 (2)"]

        if name == "VCCONE":
            name = "VCC_ONE"

        if not bank:
            if name == "VCC_ONE" or "GND" in name or name.startswith("VCCA"):
                bank = "PWR"
            elif "ADC" in name or "ANAIN" in name:
                bank = "ADC"
            elif name.startswith("VCCIO"):
                bank = name[5:]
            else:
                assert False, name

        fullname = "/".join(filter(bool, [name, row["Optional Function(s)"], row["Configuration Function"]]))
        if any(s in fullname for s in ["JTAGEN", "TMS", "TCK", "TDI", "TDO",
                                       "DEV_", "CONF", "CRC", "nSTATUS"]):
            bank = "JTAG"
        partn = partbanks[bank]

        com.add_pin(Pin(fullname, pin, x=0, y=ys[partn - 1], part=partn, length=200))
        ys[partn - 1] += 100

lib.save("10M08SAE144.lib")
