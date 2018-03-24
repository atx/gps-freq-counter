
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


class VerilogInitializedMemoryBase(resourceName: String) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val bus = Flipped(new MemoryBus)
  })
  val resource = getClass.getResource("/" + resourceName)
  val nwords = VerilogInitializedMemory.loadVerilogHexFromStream(resource.openStream()).length
  val path = java.nio.file.Paths.get(resource.toURI()).toAbsolutePath().toString()
  setInline("VerilogInitializedMemoryBase.v",
    s"""
    |module VerilogInitializedMemoryBase(
    |  input clock,
    |  input bus_valid,
    |  output reg bus_ready,
    |  input [31:0] bus_addr,
    |  input [31:0] bus_wdata,
    |  input [3:0] bus_wstrb,
    |  input bus_instr,
    |  output reg [31:0] bus_rdata
    |  );
    |reg [31:0] mem [0:$nwords];
    |initial begin
    |    $$readmemh("$path", mem);
    |end

    |always @(posedge clock) begin
    |  if (bus_valid) begin
    |    bus_rdata <= mem[bus_addr >> 2];
    |    bus_ready <= 1;
    |  end else begin
    |    bus_ready <= 0;
    |  end
    |end
    |endmodule
    """.stripMargin)
}


object VerilogInitializedMemory {
  def loadVerilogHexFromStream(stream: java.io.InputStream) : Seq[Long] = {
    Iterator.continually(stream.read).takeWhile(_ != -1)
      .map(_.toChar).mkString.split(" +").map(java.lang.Long.parseLong(_, 16))
  }
}


class VerilogInitializedMemory(val resourceName: String) extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
  })

  val base = Module(new VerilogInitializedMemoryBase(resourceName))
  io.bus <> base.io.bus
  base.io.clock := clock
}
