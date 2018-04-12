#! /bin/bash

set -e

COMMAND="$1"
PROJECT="gps-freq-counter"

function log()
{
	echo -ne '\033[1;36m'
	echo "[STATUS]: " "$@"
	echo -ne '\033[0m'
}

cd $(dirname $0)

OUTDIR=output_files
SOFFILE=${OUTDIR}/${PROJECT}.sof
SVFFILE=${OUTDIR}/${PROJECT}.svf

log "Building the firmware"
pushd ../../src/build
make
popd

log "Building Chisel files"
pushd ..
sbt run
popd

if [ "$COMMAND" != "mifonly" ]; then
	log "Compiling"
	quartus_sh --flow compile ${PROJECT}
else
	log "Rebuilding MIFs"
	quartus_cdb --update_mif ${PROJECT}
	quartus_asm ${PROJECT}
fi

log "Creating SVF"
quartus_cpf -c -q 12.0MHz -g 3.3V -n p ${SOFFILE} ${SVFFILE}

log "Flashing"
jtag ./flash.urjtag

rm $SVFFILE
