
package gfc

import chisel3._
import chisel3.iotesters.{PeekPokeTester}


class PPSCounterTester(c: PPSCounter) extends PeekPokeTester(c) {
  poke(c.io.pps, false.B)
  poke(c.io.signal, false.B)
  step(10)
  poke(c.io.pps, true.B)
  poke(c.io.signal, true.B)
  step(1)

  def doSignal(nCycles: Int) = {
    for (x <- 1 to nCycles) {
      poke(c.io.signal, true.B)
      step(5)
      if (x >= nCycles * 0.2) {
        poke(c.io.pps, false.B)
      }
      step(5)
      poke(c.io.signal, false.B)
      step(10)
    }
  }

  doSignal(100)

  poke(c.io.signal, true.B)
  poke(c.io.pps, true.B)

  step(1)
  expect(c.io.bus.rdata, 100.U)

  step(10)
  poke(c.io.pps, false.B)
  poke(c.io.signal, false.B)
  step(10)
  poke(c.io.pps, true.B)
  step(1)
  expect(c.io.bus.rdata, 1.U)

  step(100)
}


class PPSCounterTests extends GFCSpec {
  should("count frequency pulses", () => new PPSCounter, new PPSCounterTester(_))
}
