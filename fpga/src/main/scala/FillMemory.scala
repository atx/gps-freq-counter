
package gfc

import chisel3._
import chisel3.util._

class FillMemory(word: UInt) extends Module {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryBus)
  })

  io.mem.ready := true.B
  when (io.mem.valid) {
    io.mem.rdata := word
  } .otherwise {
    io.mem.rdata := 0x00000000.U
  }
}
