
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester}

abstract class PicoRVBaseTester(c: PicoRV) extends PeekPokeTester(c) {
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

class PicoRVSimpleTester(c: PicoRV) extends PicoRVBaseTester(c) {
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

class PicoRVIntegrationTestWrapper extends Module {
 val io = IO(new Bundle {
   val out = Output(UInt(32.W))
 })

 val firmware = List(
   0xcafec5b7l,
   0xabe58593l,
   0x20000537l,
   0x00b52023l
   ) map (_.U(32.W))

 val fwMem = Module(new ReadOnlyMemory(firmware))
 val outReg = Module(new Register)
 io.out := outReg.io.value

 val mux = Module(new MemoryMux(List(
   0x000000.U(24.W),
   0x2000000.U(28.W)
   )))

 val rv = Module(new PicoRV)

 mux.io.slaves(0) <> fwMem.io.bus
 mux.io.slaves(1) <> outReg.io.bus
 mux.io.master <> rv.io.mem
}

class PicoRVIntegrationTester(c: PicoRVIntegrationTestWrapper) extends PeekPokeTester(c) {
  step(100)
  expect(c.io.out, 0xcafebabel)
}


abstract class PicoRVBaseFirmwareTestWrapper(resourceName: String) extends Module {
  private val inputStream = getClass.getResourceAsStream("/" + resourceName)
  private val stream = Stream.continually(inputStream.read)
    .takeWhile(_ != -1).map(_.longValue).grouped(4)
  val firmware = (stream map { v =>
    (v(0) <<  0) | (v(1) <<  8) | (v(2) << 16) | (v(3) << 24)
  } map (_.U(32.W))).toList
  val fwMem = Module(new ReadOnlyMemory(firmware))
}

class PicoRVFibonnaciTestWrapper extends PicoRVBaseFirmwareTestWrapper("picorv_test_fib.bin") {
  val io = IO(new Bundle {
    val out = Output(UInt(32.W))
  })

  val outReg = Module(new Register)
  io.out := outReg.io.value

  val rwMem = Module(new Memory(1024 * 12/4))
  val stackMem = Module(new Memory(1024))

  val mux = Module(new MemoryMux(List(
    0x0000.U(16.W),
    0x2000.U(16.W),
    0x4000000.U(28.W),
    0xfffff.U(20.W)
    )))

  mux.io.slaves(0) <> fwMem.io.bus
  mux.io.slaves(1) <> rwMem.io.bus
  mux.io.slaves(2) <> outReg.io.bus
  mux.io.slaves(3) <> stackMem.io.bus

  val rv = Module(new PicoRV)
  mux.io.master <> rv.io.mem
}

class PicoRVFibonnaciTester(c: PicoRVFibonnaciTestWrapper) extends PeekPokeTester(c) {
  step(7000)
  expect(c.io.out, 0x2f4b)
}


class PicoRVTests extends ChiselFlatSpec {
  val args = Array("--backend-name", "verilator")
  "PicoRV" should "correctly execute instructions" in {
    iotesters.Driver.execute(args, () => new PicoRV) {
      c => new PicoRVSimpleTester(c)
    } should be (true)
  }
  "PicoRV" should "correctly interface with a memory mapped device" in {
    iotesters.Driver.execute(args, () => new PicoRVIntegrationTestWrapper) {
      c => new PicoRVIntegrationTester(c)
    } should be (true)
  }
  "PicoRV" should "produce correct result with test1" in {
    iotesters.Driver.execute(args, () => new PicoRVFibonnaciTestWrapper) {
      c => new PicoRVFibonnaciTester(c)
    } should be (true)
  }
}
