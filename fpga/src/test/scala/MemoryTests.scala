
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, Driver}

class MemoryTester(c: Memory) extends PeekPokeTester(c) {
  poke(c.io.bus.valid, false.B)
  poke(c.io.bus.instr, false.B)
  poke(c.io.bus.addr, 0.U(32.W))
  poke(c.io.bus.wdata, 0.U(32.W))
  poke(c.io.bus.wstrb, 0.U(4.W))
  step(2)

  def read(address: UInt) = {
    poke(c.io.bus.valid, true.B)
    poke(c.io.bus.wstrb, 0.U)
    poke(c.io.bus.addr, address)
  }

  def write(address: UInt, value: UInt, mask: UInt) = {
    poke(c.io.bus.valid, true.B)
    poke(c.io.bus.wstrb, mask)
    poke(c.io.bus.addr, address)
    poke(c.io.bus.wdata, value)
  }

  val testdata = List(
    // address, write, mask, expect
    ( 0x0.U, "x12341234".U, "b1111".U, "x12341234".U),
    ( 0x4.U, "xcafebabe".U, "b1111".U, "xcafebabe".U),
    ( 0x0.U, "x44445555".U, "b1111".U, "x44445555".U),
    (null, null, null, null),
    (0x64.U, "xdeadbeef".U, "b1111".U, "xdeadbeef".U),
    (null, null, null, null),
    (null, null, null, null),
    (0x32.U, "xabcdabcd".U, "b1111".U, "xabcdabcd".U),
    (0x32.U, "x11111111".U, "b0011".U, "xabcd1111".U),
    (0x32.U, "x23232323".U, "b0001".U, "xabcd1123".U),
    (0x32.U, "x88888888".U, "b1000".U, "x88cd1123".U),
    (0x32.U, "xfbfcfdfe".U, "b1100".U, "xfbfc1123".U),
    (null, null, null, null)
  )

  for ((address, data, mask, expected) <- testdata) {
    if (address == null) {
      poke(c.io.bus.valid, false.B)
      step(1)
    } else {
      write(address, data, mask)
      step(1)
      read(address)
      step(1)
      expect(c.io.bus.rdata, expected)
      expect(c.io.bus.valid, true.B)
    }
  }
}

object ReadOnlyMemoryTester {
  private val rng = new scala.util.Random(42)
  val initData = (0 to 128) map ((_) => rng.nextInt(2147483647).U(32.W))
}

class ReadOnlyMemoryTester(c: ReadOnlyMemory) extends PeekPokeTester(c) {
  val rng = new scala.util.Random(42)
  for (idx <- rng.shuffle(0 to ReadOnlyMemoryTester.initData.length - 1)) {
    poke(c.io.bus.valid, true.B)
    poke(c.io.bus.addr, (idx << 2).U)
    step(1)
    expect(c.io.bus.rdata, ReadOnlyMemoryTester.initData(idx))
    expect(c.io.bus.ready, true.B)
  }
}

class MemoryTests extends ChiselFlatSpec {
  "Memory" should "handle writes and reads correctly" in {
    val args = Array("--fint-write-vcd")
    iotesters.Driver.execute(args, () => new Memory(1024)) {
      c => new MemoryTester(c)
    } should be (true)
  }
  "ReadOnlyMemory" should "properly initialize and handle reads" in {
    val args = Array("--fint-write-vcd")
    iotesters.Driver.execute(args, () => new ReadOnlyMemory(ReadOnlyMemoryTester.initData)) {
      c => new ReadOnlyMemoryTester(c)
    } should be (true)
  }
}
