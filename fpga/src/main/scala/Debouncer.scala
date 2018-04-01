
package gfc

import chisel3._
import chisel3.util._


object Debouncer {
  def apply(input: Bool, divider: Int, threshold: Int) : Bool = {
    val deb = Module(new Debouncer(divider, threshold))
    deb.io.input := input
    deb.io.output
  }
}


class Debouncer(val divider: Int, val threshold: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(Bool())
    val output = Output(Bool())
  })

  val state = RegInit(false.B)
  io.output := state

  val counter = RegInit(threshold.U)

  when (Counter(divider).inc()) {
    when (io.input =/= state) {
      when (counter === 0.U) {
        state := !state
        counter := threshold.U
      } otherwise {
        counter := counter - 1.U
      }
    } otherwise {
      counter := threshold.U
    }
  }
}
