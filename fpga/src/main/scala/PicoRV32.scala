
package gfc

import chisel3._
import chisel3.experimental._


class MemoryBus extends Bundle {
  val valid = Output(Bool())
  val instr = Output(Bool())
  val ready = Input(Bool())

  val addr = Output(UInt(32.W))
  val wdata = Output(UInt(32.W))
  val wstrb = Output(UInt(4.W))
  val rdata = Input(UInt(32.W))
}


class picorv32 extends BlackBox(Map("ENABLE_IRQ" -> 1)) {

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val resetn = Input(Bool())

    val mem = new MemoryBus()

    val irq = Input(UInt(32.W))
    val eoi = Output(UInt(32.W))
  })

}


class PicoRV32 extends Module {
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

