
package gfc

import chisel3.{Module}
import chisel3.iotesters.{PeekPokeTester}

abstract class BetterPeekPokeTester[+T <: Module](private val c: T) extends PeekPokeTester(c) {

  def nSteps(count: Int)(block: => Unit) = {
    for (_ <- 1 to count) {
      block
      step(1)
    }
  }

  def stepWhile(cond: => Boolean, timeout: Int = -1)(block: => Unit) = {
    val iter = Iterator.from(0).takeWhile { t => cond && (timeout == -1 || t <= timeout) }
    for (t <- iter) {
      if (t == timeout) {
        expect(false, "Operation timed out!")
        finish  // This is likely critical anyway
      } else {
        block
        step(1)
      }
    }
  }

}
