
package gfc

import chisel3._
import chisel3.util._
import chisel3.experimental._


class MemoryBus extends Bundle {
  val valid = Output(Bool())
  val instr = Output(Bool())
  val ready = Input(Bool())

  val addr = Output(UInt(32.W))
  val wdata = Output(UInt(32.W))
  val wstrb = Output(UInt(4.W))
  val rdata = Input(UInt(32.W))

  def isWrite = valid && wstrb =/= 0.U
  def isRead = valid && wstrb === 0.U
  def wordAddress = addr >> 2

  def wdataAsByteVec = Vec(Array(wdata(7, 0), wdata(15, 8), wdata(23, 16), wdata(31, 24)))
  def wstrbAsArray = Array(wstrb(0), wstrb(1), wstrb(2), wstrb(3))
}


object picorv32 {
  private val config = Map[String,Param](
      "ENABLE_IRQ" -> 0,
      "PROGADDR_RESET" -> 0x00000000l,
      "ENABLE_REGS_16_31" -> 1,
      "ENABLE_COUNTERS" -> 0,
      "CATCH_ILLINSN" -> 0
    )
}


class picorv32 extends BlackBox(picorv32.config) with HasBlackBoxResource {

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val resetn = Input(Bool())

    val mem = new MemoryBus()

    val irq = Input(UInt(32.W))
    val eoi = Output(UInt(32.W))
  })

  setResource("/picorv32.v")

}


class PicoRV extends Module {
  val io = IO(new Bundle {
    val mem = new MemoryBus()
    val irq = Input(UInt(32.W))
  })

  val pico = Module(new picorv32());

  pico.io.clk := clock
  pico.io.resetn := !reset.toBool

  pico.io.mem <> io.mem
  pico.io.irq <> io.irq
}

