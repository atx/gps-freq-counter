
package gfc

import chisel3._
import chisel3.iotesters.{PeekPokeTester}

class CRCTester(c: CRC) extends PeekPokeTester(c) {
  poke(c.io.valid, false.B)
  step(10)
  poke(c.io.clear, true.B)
  step(1)
  poke(c.io.clear, false.B)
  expect(c.io.result, 0x0000)
  step(2)
  val data = List(
      0x09, 0x02, 0x22, 0x00, 0x01, 0x01, 0x00, 0xa0
    )
  for (b <- data) {
    poke(c.io.input, b)
    poke(c.io.valid, true.B)
    step(1)
    poke(c.io.input, 0)
    poke(c.io.valid, false.B)
    step(7)
  }
  // Not yet
  step(1)
  expect(c.io.result, 0x980a)

  step(20)
}


class CRCTests extends gfc.GFCSpec {
  should("compute CRC", () => new CRC(0xa001.U(16.W), 8), new CRCTester(_))
}
