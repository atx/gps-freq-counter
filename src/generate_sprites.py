#! /usr/bin/env python3

import collections
import itertools
import math
import sys
import urllib.request
import PIL
import PIL.BdfFontFile


def bytecount_from_dimensions(width, height):
    return width * math.ceil(height / 8)


def bdf_from_url(url):
    bdf = PIL.BdfFontFile.BdfFontFile(urllib.request.urlopen(url))
    bdf.compile()
    return bdf


def image_to_sprite(img):
    data = bytearray(bytecount_from_dimensions(img.width, img.height))
    for x, y in itertools.product(range(img.width), range(img.height)):
        val = img.getpixel((x, y))
        if isinstance(val, collections.Iterable):
            val = val[0]  # Pick the red channel in that case - who cares really
        if val > 0:
            data[x + y//8*img.width] |= 1 << (y % 8)
    return img.width, img.height, data


def format_hex_bytes(data):
    return ", ".join("0x{:02x}".format(d) for d in data)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-s", "--struct-name",
        required=True
    )
    subparsers = parser.add_subparsers(dest="action")
    bdf_parser = subparsers.add_parser("bdf")
    bdf_parser.add_argument(
        "-u", "--url",
        required=True,
    )
    bdf_parser.add_argument(
        "-c", "--chars",
        default=""
    )
    bdf_parser.add_argument(
        "--ascii",
        action="store_true"
    )
    img_parser = subparsers.add_parser("img")
    img_parser.add_argument(
        "-i", "--image",
        required=True
    )
    args = parser.parse_args()

    f = sys.stdout
    if args.action == "bdf":
        if args.ascii:
            args.chars = [chr(x) for x in range(ord("!"), ord("~")+1)]

        bdf = bdf_from_url(args.url)
        size = bdf[ord("a")][-1].size
        f.write("\n".join([
            "// Generated from {}".format(args.url),
            "static const struct font {} = {{".format(args.struct_name),
            "\t.width = {},".format(size[0]),
            "\t.height = {},".format(size[1]),
            "\t.stride = {},".format(bytecount_from_dimensions(size[0], size[1])),
            "\t.entries = {\n"
        ]))
        for c in args.chars:
            *_, img = bdf[ord(c)]
            width, height, data = image_to_sprite(img)
            assert (width, height) == size  # Monospace only!
            f.write(
                "\t\t" +
                format_hex_bytes(data) +
                ", // '{}'\n".format(c)
            )
        f.write("\t}\n};\n\n")
    if args.action == "img":
        img = PIL.Image.open(args.image)
        width, height, data = image_to_sprite(img)
        f.write("\n".join([
            "// Generated from '{}'".format(args.image),
            "static const struct sprite {} = {{".format(args.struct_name),
            "\t.width = {},".format(width),
            "\t.height = {},".format(height),
            "\t.data = {{ {} }}".format(format_hex_bytes(data)),
            "};",
            ""
        ]))
