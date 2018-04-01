#! /bin/bash

OUTFILE=$1
GENSCRIPT=$(dirname $(realpath $0))/generate_sprites.py

#echo "Generating sprites to ${OUTFILE}"

echo "" > ${OUTFILE}
echo "// Automatically generated file" >> ${OUTFILE}
echo "" >> ${OUTFILE}

function do_font {
	$GENSCRIPT -s $1 bdf -u $2 -c $3 >> ${OUTFILE}
}

function do_font_ascii {
	$GENSCRIPT -s $1 bdf -u $2 --ascii >> ${OUTFILE}
}

function do_img {
	$GENSCRIPT -s $1 img -i $2 >> ${OUTFILE} 
}

do_font font_small_digits https://cgit.freedesktop.org/xorg/font/misc-misc/plain/5x7.bdf 0123456789
do_font_ascii font_small_ascii https://cgit.freedesktop.org/xorg/font/misc-misc/plain/5x7.bdf

for file in ./sprites/*.png; do
	filename=$(basename $file)
	structname=${filename%.*}
	do_img sprite_${structname} $file
done
