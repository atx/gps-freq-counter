
package gfc

import chisel3._
import chisel3.util._


class SPIBundle extends Bundle {
  val mosi = Output(Bool())
  val clk = Output(Bool())
  val cs = Output(Bool())
}


class SPI(val divider: Int = 10, val memSize: Int = 256) extends Module {
  require(divider % 2 == 0)
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
    val spi = new SPIBundle
    val status = new Bundle {
      val idle = Output(Bool())
    }
  })

  val sIdle :: sFirst :: sRun :: sLast :: Nil = Enum(4)

  val state = RegInit(sIdle)
  io.status.idle := state === sIdle
  io.spi.cs := state === sIdle || state === sLast

  val baseCntr = Counter(divider)

  val mem = SyncReadMem(memSize, Vec(4, UInt(8.W)))

  io.spi.clk := true.B

  val ptr = Reg(UInt(log2Ceil(memSize * 32).W), init = 0.U)
  val byteCount = Reg(UInt(log2Ceil(memSize * 8).W))
  val wordPtr = ptr / 32.U
  val readBuffer = Reg(UInt(32.W))

  io.bus.ready := RegNext(io.bus.valid)
  Memory.connectWrite(mem, io.bus)

  val readValueRaw = mem.read(Mux(state === sIdle, io.bus.wordAddress, wordPtr))
  io.bus.rdata := readValueRaw.toBits

  val readValue = Vec(readValueRaw(3), readValueRaw(2),
                      readValueRaw(1), readValueRaw(0)).toBits

  io.spi.mosi := Mux(state === sRun, readBuffer(31), true.B)

  val writingToControl = io.bus.valid && io.bus.addr === 0.U && io.bus.wstrb =/= 0.U

  switch (state) {
    is (sIdle) {
      when (writingToControl) {
        ptr := (io.bus.wdata(7, 0) + 1.U) * 32.U
        byteCount := io.bus.wdata(31, 8)
        state := sFirst
      }
    }
    is (sFirst) {
      when (baseCntr.inc()) {
        state := sRun
        readBuffer := readValue
        ptr := ptr + 1.U
      }
    }
    is (sRun) {
      io.spi.clk := baseCntr.value >= (divider / 2).U
      when (baseCntr.inc()) {
        when (byteCount === 1.U && ptr % 8.U === 0.U) {
          state := sLast
        } otherwise {
          when (ptr % 8.U === 0.U) {
            byteCount := byteCount - 1.U
          }
          when (ptr % 32.U === 0.U) {
            readBuffer := readValue
          } otherwise {
            readBuffer := readBuffer << 1.U
          }

          ptr := ptr + 1.U
        }
      }
    }
    is (sLast) {
      when (baseCntr.inc()) {
        state := sIdle
      }
    }
  }
}
