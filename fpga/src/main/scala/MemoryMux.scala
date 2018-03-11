
package gfc

import chisel3._
import chisel3.util._

class MemoryMux(slave_prefixes: Seq[(Long, Int)]) extends Module {
  val io = IO(new Bundle {
    val master = Flipped(new MemoryBus)
    val slaves = Vec(slave_prefixes.size, new MemoryBus)
  })

  val extracted_prefixes = Wire(Vec(slave_prefixes.size, UInt(32.W)))
  val selectors = Wire(Vec(slave_prefixes.size, Bool()))

  for (i <- 0 until slave_prefixes.size) {
    val (prefix, width) = slave_prefixes(i)
    val rest_width = io.master.addr.getWidth - width
    extracted_prefixes(i) := io.master.addr >> rest_width
    selectors(i) := (prefix.U(32.W) >> rest_width) === extracted_prefixes(i)

    io.slaves(i) <> io.master
    io.slaves(i).valid := selectors(i) && io.master.valid
    io.slaves(i).addr := io.master.addr & ("b" + "1"*rest_width).U
  }

  io.master.ready := MuxCase(false.B,
    (io.slaves zip selectors) map { case (sio, sel) =>  sel -> sio.ready})

  io.master.rdata := MuxCase("x01020304".U,
    (io.slaves zip selectors) map { case (sio, sel) =>
      sel -> sio.rdata
    })
}
