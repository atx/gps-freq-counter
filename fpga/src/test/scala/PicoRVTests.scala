
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


abstract class PicoRVBaseFirmwareTestWrapper(resourceName: String) extends Module {
  val fwMem = ReadOnlyMemory.fromResource(resourceName)
  val rwMem = Module(new Memory(1024 * 12/4))
  val stackMem = Module(new Memory(1024))

  val mmDevicesBase = List(
    (0x00000000l, 18, fwMem.io.bus),
    (0x20000000l, 18, rwMem.io.bus),
    (0xfffff000l, 20, stackMem.io.bus)
    )
  val mmDevices: Seq[Tuple3[Long, Int, MemoryBus]]

  val picorv = Module(new PicoRV)
  val memoryMux = MemoryMux.build(picorv.io.mem, mmDevicesBase ++ mmDevices)
}

class PicoRVFibonnaciTestWrapper extends PicoRVBaseFirmwareTestWrapper("picorv_test_fib.bin") {
  val io = IO(new Bundle {
    val out = Output(UInt(32.W))
  })

  lazy val outReg = Module(new Register)
  io.out := outReg.io.value

  lazy val mmDevices = List(
    (0x40000000l, 28, outReg.io.bus)
    )
}

class PicoRVFibonnaciTester(c: PicoRVFibonnaciTestWrapper) extends PeekPokeTester(c) {
  step(7000)
  expect(c.io.out, 0x2f4b)
}

class PicoRVSPITestWrapper extends PicoRVBaseFirmwareTestWrapper("picorv_test_spi.bin") {
  val io = IO(new Bundle {
    val spi = new Bundle {
      val mosi = Output(Bool())
      val clk = Output(Bool())
    }
  })

  lazy val spi = Module(new SPI)
  io.spi <> spi.io.spi

  lazy val mmDevices = List(
    (0x30000000l, 22, spi.io.bus)
    )
}

class PicoRVSPITester(c: PicoRVSPITestWrapper) extends BetterPeekPokeTester(c) {

  // TODO: DRY... (SPITests)
  def readSPIByte() : Int = {
    var ret = 0x00
    for (_ <- 0 to 7) {
      stepWhile(peek(c.io.spi.clk) == 1, 10) {}
      nSteps(5) {
        expect(c.io.spi.clk, false.B)
      }
      expect(c.io.spi.clk, true.B)
      ret = (ret << 1) | peek(c.io.spi.mosi).toInt
    }
    return ret
  }

  val expectedData = List(
	0xaa, 0x1d, 0xbe, 0xef, 0xaa, 0x10, 0x41
  )
  stepWhile(peek(c.io.spi.clk) == 1, 3000) {}

  for (exp <- expectedData) {
    val read = readSPIByte()
    expect(read == exp, s"$read == $exp")
  }
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
  "PicoRV" should "correctly interact with the SPI peripheral" in {
    iotesters.Driver.execute(args, () => new PicoRVSPITestWrapper) {
      c => new PicoRVSPITester(c)
    } should be (true)
  }
}
