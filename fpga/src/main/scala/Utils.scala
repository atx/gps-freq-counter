
package gfc

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog


object Utils {

  def synchronize(i: Bool) : Bool = {
    return RegNext(RegNext(i))
  }

  def fallingEdge(i: Bool) : Bool = {
    val v = RegNext(i)
    return v && !i
  }

  def risingEdge(i: Bool) : Bool = {
    val v = RegNext(i)
    return !v && i
  }

  def anyEdge(i: Bool) : Bool = {
    val v = RegNext(i)
    return v =/= i
  }

  def maskedWrite(mask: UInt, wordYes: UInt, wordNo: UInt) : UInt = {
    // This works only for 32-bit word and per-byte masks at this point...
    val bytes = (0 to 3) map { i => Mux(mask(i), wordYes, wordNo)(8*(i+1)-1, 8*i) }
    Cat(bytes(3), bytes(2), bytes(1), bytes(0))
  }

  def maskedWrite(bus: MemoryBus, otherwise: UInt) : UInt = {
    maskedWrite(bus.wstrb, bus.wdata, otherwise)
  }
}


class InoutBundle extends Bundle {
  val pin = Analog(1.W)
  val oe = Output(Bool())
  val read = Input(Bool())
  val write = Output(Bool())
}


class InoutWrapper extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val clock = Input(Clock())
    val d = Flipped(new InoutBundle)
  })

  setInline("InoutWrapper.v",
    s"""
    |module InoutWrapper(
    |    input reset,
    |    input clock,
    |    inout d_pin,
    |    input d_oe,
    |    output reg d_read,
    |    input d_write
    |  );

    |reg in_b;
    |reg out_b;
    |reg oe_b;

    |assign d_pin = oe_b ? out_b : 1'bZ;

    |always @(posedge clock) begin
    |  out_b <= d_write;
    |  oe_b <= d_oe;

    |  in_b <= d_pin;
    |  d_read <= in_b;
    |end

    |endmodule
    |
    """.stripMargin)
}
