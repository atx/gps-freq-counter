
package gfc

import chisel3._
import chisel3.util._

object MemoryMux {
  def build(master: MemoryBus, devs: Seq[Tuple3[Long, Int, MemoryBus]]) : MemoryMux = {
    val mux = Module(new MemoryMux(devs map {x => (x._1, x._2)}))
    for (((_, _, devBus), muxBus) <- devs zip mux.io.slaves) {
      muxBus <> devBus
    }
    mux.io.master <> master
    return mux
  }

  def singulars(base: Long, devs: MemoryBus*) : Seq[Tuple3[Long, Int, MemoryBus]] = {
    devs.zipWithIndex.map { case (d, i) => (base + 4*i, 30, d) }
  }
}

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
  val validAddress = selectors.asUInt =/= 0.U

  // TODO: More sophisticated invalid address handling?
  io.master.ready := Mux(validAddress, Mux1H(selectors zip io.slaves.map(_.ready)), false.B)
  io.master.rdata := Mux1H(selectors zip io.slaves.map(_.rdata))
}
