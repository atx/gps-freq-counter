
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, Driver}

abstract class PicoRVTesterBase(c: PicoRV) extends PeekPokeTester(c) {
  val memory: Array[BigInt]
  def runProcessor(numSteps: Int) = {
    poke(c.io.mem.ready, false.B)
    poke(c.io.mem.rdata, 0.U(32.W))
    step(20)
    for (_ <- 0 to numSteps) {
      if (peek(c.io.mem.valid) == 1) {
        poke(c.io.mem.ready, true.B)
        val wstrb = peek(c.io.mem.wstrb).intValue
        val addr = peek(c.io.mem.addr).intValue >> 2
        if (wstrb == 0) {
          poke(c.io.mem.rdata, memory(addr).U(32.W))
        } else {
          assert(wstrb == 0xf)  // TODO
          memory(addr) = peek(c.io.mem.wdata)
        }
      } else {
        poke(c.io.mem.ready, false.B)
      }
      step(1)
    }
  }
}

class PicoRVTesterSimple(c: PicoRV) extends PicoRVTesterBase(c) {
  val memory = Array[BigInt](
    0xdeadc5b7l,  // lui a1,0xdeadc
    0xeef58593l,  // addi    a1,a1,-273
    0x01800513l,  // li  a0,24
    0x00b52023l,  // sw  a1,0(a0)
    0x0000006fl,  // j 10  (infinite loop)
    0x00000000l,
    0x00000000l   // 0xdeadbeef should appear here
  )

  runProcessor(30)

  assert(memory(6) == 0xdeadbeefl)
}


class PicoRVTests extends ChiselFlatSpec {
  "PicoRV" should "correctly execute instructions" in {
    val args = Array("--backend-name", "verilator")
    iotesters.Driver.execute(args, () => new PicoRV) {
      c => new PicoRVTesterSimple(c)
    } should be (true)
  }
}
