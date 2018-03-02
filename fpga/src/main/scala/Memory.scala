
package gfc

import chisel3._
import chisel3.util._

class Memory(nwords: Int) extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
  })

  io.bus.ready := true.B

  val write = io.bus.valid && io.bus.wstrb =/= 0.U
  val read = io.bus.valid && io.bus.wstrb === 0.U
  var wordAddress = io.bus.addr >> 2

  val mem = SyncReadMem(nwords, Vec(4, UInt(8.W)))

  when (write) {
    val inBitVec = Vec(Array(io.bus.wdata(7, 0),
                             io.bus.wdata(15, 8),
                             io.bus.wdata(23, 16),
                             io.bus.wdata(31, 24)))
    val inMask = Array(io.bus.wstrb(0),
                       io.bus.wstrb(1),
                       io.bus.wstrb(2),
                       io.bus.wstrb(3))
    mem.write(wordAddress, inBitVec, inMask)
    io.bus.rdata := DontCare
  } .otherwise {
    io.bus.rdata := mem.read(wordAddress).toBits
  }
}

class ReadOnlyMemory(data: Seq[UInt]) extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
  })

  private val rom = Vec(data)
  io.bus.ready := true.B
  io.bus.rdata := rom(io.bus.addr >> 2)
}
