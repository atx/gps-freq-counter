
package gfc

import chisel3._
import chisel3.util._

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
