
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


class InputRegister extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
    val value = Input(UInt(32.W))
  })

  io.bus.ready := true.B
  io.bus.rdata := io.value
}


object OutputRegister {
  def build(outs: Tuple2[Bool, Boolean]*) : OutputRegister = {
    require(outs.length <= 32)
    val initVal = outs.map(_._2).foldRight(0l)
      { (default, acc) => (acc << 1) | (if (default) 1 else 0) }
    val reg = Module(new OutputRegister(initVal.U(outs.length.W)))
    for (i <- 0 until outs.length) {
      outs(i)._1 := reg.io.value(i)
    }
    return reg
  }
}


class OutputRegister(initVal: UInt) extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
    val value = Output(UInt(initVal.getWidth.W))
  })

  val reg = RegInit(initVal)
  io.value := reg

  io.bus.rdata := reg
  io.bus.ready := true.B

  val writes = (0 to 3) map { i => Mux(io.bus.wstrb(i), io.bus.wdata, reg)(8*(i+1)-1, 8*i) }
  val maskedWrite = Cat(writes(3), writes(2), writes(1), writes(0))

  when (io.bus.valid && io.bus.wstrb =/= 0.U) {
    reg := maskedWrite
  }
}


class TimerRegister(val divider: Int) extends Module {
  require(divider >= 1)
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
  })

  val reg = RegInit(0.U(32.W))

  io.bus.rdata := reg
  io.bus.ready := true.B

  when (Counter(divider).inc()) {
    reg := reg + 1.U
  }
}
