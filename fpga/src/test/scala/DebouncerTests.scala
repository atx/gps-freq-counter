

package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec}


class DebouncerTester(c: Debouncer) extends BetterPeekPokeTester(c) {
  val ins = List(
    0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0
    )
  val exps = List(
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,0
    )

  for ((i, e) <- ins zip exps) {
    poke(c.io.input, i)
    expect(c.io.output, e)
    step(c.divider)
  }
  step(10)
}


class DebouncerTests extends ChiselFlatSpec {
  val args = Array("--fint-write-vcd")
  "Debouncer" should "debounce" in {
    iotesters.Driver.execute(args, () => new Debouncer(10, 5)) {
      c => new DebouncerTester(c)
    } should be (true)
  }
}
