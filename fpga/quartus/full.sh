#! /bin/bash

set -e

function log()
{
	echo -ne '\033[1;36m'
	echo "[STATUS]: " "$@"
	echo -ne '\033[0m'
}

cd $(dirname $0)

OUTDIR=output_files
SOFFILE=${OUTDIR}/gps-freq-counter.sof
SVFFILE=${OUTDIR}/gps-freq-counter.svf

log "Building the firmware"
pushd ../../src/build
make
popd

log "Building Chisel files"
pushd ..
sbt run
popd

log "Compiling"
quartus_sh --flow compile gps-freq-counter

log "Creating SVF"
quartus_cpf -c -q 12.0MHz -g 3.3V -n p $SOFFILE $SVFFILE

log "Flashing"
jtag ./flash.urjtag

rm $SVFFILE
