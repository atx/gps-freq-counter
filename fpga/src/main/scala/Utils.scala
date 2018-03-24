
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
}
