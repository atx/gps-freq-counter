
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec}


class PPSCounterTester(c: PPSCounter) extends BusTester(c, c.io.bus) {

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


class PPSCounterTests extends ChiselFlatSpec {
  val args = Array("--fint-write-vcd")
  "PPSCounter" should "count frequency pulses" in {
    iotesters.Driver.execute(args, () => new PPSCounter) {
      c => new PPSCounterTester(c)
    } should be (true)
  }
}
