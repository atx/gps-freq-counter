
package gfc

import chisel3._
import chisel3.util._

object Memory
{
  def connectMemory(mem: SyncReadMem[Vec[UInt]], bus: MemoryBus) = {
    val write = bus.valid && bus.wstrb =/= 0.U
    val read = bus.valid && bus.wstrb === 0.U
    var wordAddress = bus.addr >> 2
    bus.ready := RegNext(bus.valid)

    when (write) {
      val inBitVec = Vec(Array(bus.wdata(7, 0),
                               bus.wdata(15, 8),
                               bus.wdata(23, 16),
                               bus.wdata(31, 24)))
      val inMask = Array(bus.wstrb(0),
                         bus.wstrb(1),
                         bus.wstrb(2),
                         bus.wstrb(3))
      mem.write(wordAddress, inBitVec, inMask)
      bus.rdata := DontCare
    } .otherwise {
      bus.rdata := mem.read(wordAddress).toBits
    }
  }
}

class Memory(nwords: Int) extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
  })

  val mem = SyncReadMem(nwords, Vec(4, UInt(8.W)))
  Memory.connectMemory(mem, io.bus)
}

object ReadOnlyMemory {
  def fromResource(resourceName: String) : ReadOnlyMemory = {
    val inputStream = getClass.getResourceAsStream("/" + resourceName)
    val stream = Stream.continually(inputStream.read)
      .takeWhile(_ != -1).map(_.longValue).grouped(4)
    val data = (stream map { v =>
      var word = 0l
      for ((c, i) <- v.zipWithIndex) {
        word |= (c << (i * 8))
      }
      word
    } map (_.U(32.W))).toList
    return Module(new ReadOnlyMemory(data))
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
