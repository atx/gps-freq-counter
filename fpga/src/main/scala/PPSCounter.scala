
package gfc

import chisel3._
import chisel3.util._


class PPSCounter extends Module {
  val io = IO(new Bundle {
    val pps = Input(Bool())
    val signal = Input(Bool())

    val bus = Flipped(new MemoryBus)

    val status = new Bundle {
      val updated = Output(Bool())
    }
  })

  // 32 bits is more than enough
  val regOut = Reg(UInt(32.W), init=0.U)
  val regMsm = Reg(UInt(32.W), init=0.U)

  io.bus.rdata := regOut
  io.bus.ready := true.B

  val ppsEdge = Utils.risingEdge(io.pps)
  val signalEdge = Utils.risingEdge(io.signal)

  io.status.updated := ppsEdge

  when (ppsEdge) {
    regOut := regMsm
    regMsm := Mux(signalEdge, 1.U, 0.U)
  } .elsewhen (signalEdge) {
    regMsm := regMsm + 1.U
  }
}
