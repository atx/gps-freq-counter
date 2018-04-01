
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

 val rv = Module(new PicoRV)
 val mux = MemoryMux.build(rv.io.mem, List(
   (0x00000000l, 24, fwMem.io.bus),
   (0x20000000l, 28, outReg.io.bus)
   ))
}

class PicoRVIntegrationTester(c: PicoRVIntegrationTestWrapper) extends PeekPokeTester(c) {
  step(100)
  expect(c.io.out, 0xcafebabel)
}


class TopWrapper(firmwareFile: String) extends Module {

  implicit val conf = TopConfig(
    isSim = true,
    firmwareFile = firmwareFile,
    mainClockFreq = 100000000,
    spiClockFreq =   10000000
  )
  val top = Module(new Top)

  val io = IO(top.io.cloneType)
  io <> top.io

  top.io.oscillator := clock
  top.io.debug.reset := reset
}


class PicoRVFibTester(c: TopWrapper) extends PeekPokeTester(c) {
  step(10000)
  expect(c.io.debug.reg, 0x2f4b)
}


class PicoRVSPITester(c: TopWrapper) extends BetterPeekPokeTester(c) {
  val spi = c.io.oled.spi

  // TODO: DRY... (SPITests)
  def readSPIByte() : Int = {
    var ret = 0x00
    for (_ <- 0 to 7) {
      stepWhile(peek(spi.clk) == 1, 10) {}
      nSteps(5) {
        expect(spi.clk, false.B)
      }
      expect(spi.clk, true.B)
      ret = (ret << 1) | peek(spi.mosi).toInt
    }
    return ret
  }

  val expectedData = List(
	0xaa, 0x1d, 0xbe, 0xef, 0xaa, 0x10, 0x41
  )
  stepWhile(peek(spi.clk) == 1, 10000) {}

  for (exp <- expectedData) {
    val read = readSPIByte()
    expect(read == exp, s"$read == $exp")
  }

  step(100)
}


class PicoRVMainTester(c: TopWrapper) extends PeekPokeTester(c) {
  // TODO: Setup inputs
  step(10000000)
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

  def runFirmware(firmwareFile: String, testerGen: TopWrapper => PeekPokeTester[TopWrapper]) = {
    iotesters.Driver.execute(args, () => new TopWrapper(firmwareFile))(testerGen) should be (true)
  }

  "PicoRV" should "produce correct result in test_fib" in {
    runFirmware("picorv_test_fib.memh", new PicoRVFibTester(_))
  }

  "PicoRV" should "produce correct result in test_spi" in {
    runFirmware("picorv_test_spi.memh", new PicoRVSPITester(_))
  }

  //"PicoRV" should "run the main program" taggedAs(Slow) in {
  //  runFirmware("gfc.memh", new PicoRVMainTester(_))
  //}
}
