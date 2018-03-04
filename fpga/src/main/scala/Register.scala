
package gfc

import chisel3._
import chisel3.util._

class Register extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
    val value = Output(UInt(32.W))
  })

  val reg = Reg(UInt(32.W), init = 0.U)
  io.value := reg
  io.bus.rdata := reg
  io.bus.ready := true.B

  when (io.bus.valid && io.bus.wstrb =/= 0.U) {
    reg := io.bus.wdata  // TODO: Masking
  }
}
