
package gfc

import chisel3._


class CRC(val polynomial: UInt, val width: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(UInt(width.W))
    val clear = Input(Bool())
    val valid = Input(Bool())
    val result = Output(UInt(polynomial.getWidth.W))
  })
  // Total latency of this block is width * nbytes + 1

  val buffer = Reg(UInt(width.W))
  val counter = RegInit(width.U)
  val result = Reg(UInt(polynomial.getWidth.W))
  io.result := ~result

  when (counter < width.U) {
    when ((buffer ^ result)(0)) {
      result := (result >> 1.U) ^ polynomial
    } otherwise {
      result := result >> 1.U
    }
    buffer := buffer >> 1.U
    counter := counter + 1.U
  }

  // User responsibility to wait
  when (io.valid) {
    buffer := io.input
    counter := 0.U
  }

  when (io.clear) {
    result := ~(0.U(polynomial.getWidth.W))
  }
}
