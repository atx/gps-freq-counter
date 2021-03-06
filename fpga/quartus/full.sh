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

if [ "$COMMAND" != "flashonly" ]; then
	log "Building the firmware"
	pushd ../../src/build
	make
	popd

	log "Building Chisel files"
	pushd ..
	sbt run
	popd
fi

if [ "$COMMAND" == "mifonly" ]; then
	log "Rebuilding MIFs"
	quartus_cdb --update_mif ${PROJECT}
	quartus_asm ${PROJECT}
elif [ "$COMMAND" == "flashonly" ]; then
	log "Not doing anything"
else
	log "Compiling"
	quartus_sh --flow compile ${PROJECT}
fi

log "Creating SVF"
quartus_cpf -c -q 12.0MHz -g 3.3V -n p ${SOFFILE} ${SVFFILE}

log "Flashing"
jtag ./flash.urjtag

rm $SVFFILE
