
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec}


abstract class RegisterTester[+T <: Module](private val c: T, val bus: MemoryBus) extends BetterPeekPokeTester(c) {
  poke(bus.valid, false.B)
  poke(bus.addr, 0x00000000.U)
  poke(bus.wdata, 0x00000000.U)
  poke(bus.wstrb, "b0000".U)

  def write(value: UInt, mask: UInt = "b1111".U) = {
    poke(bus.valid, true.B)
    poke(bus.wdata, value)
    poke(bus.wstrb, mask)
    expect(bus.ready, true.B)
    step(1)
    poke(bus.valid, false.B)
    poke(bus.wdata, 0.U)
    poke(bus.wstrb, "b0000".U)
  }

  def peekValue() = {
    expect(bus.ready, true.B)
    peek(bus.rdata)
  }

  def read() = {
    poke(bus.valid, true.B)
    poke(bus.wstrb, "b0000".U)
    expect(bus.ready, true.B)
    step(1)
    poke(bus.valid, false.B)
  }

  def expectValue(expected: UInt) = {
    expect(bus.ready, true.B)
    expect(bus.rdata, expected)
  }
}


class OutputRegisterWrapper extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)

    val value = Output(UInt(32.W))
    val out = Output(UInt(10.W))
  })

  val vecOut = Wire(Vec(10, Bool()))
  val reg = OutputRegister.build(
    (vecOut(0) -> true),
    (vecOut(1) -> true),
    (vecOut(2) -> false),
    (vecOut(3) -> false),
    (vecOut(4) -> false),
    (vecOut(5) -> true),
    (vecOut(6) -> false),
    (vecOut(7) -> true),
    (vecOut(8) -> true),
    (vecOut(9) -> false)
    )

  io.out := vecOut.toBits
  io.bus <> reg.io.bus
  io.value := reg.io.value
}


class OutputRegisterTester(c: OutputRegisterWrapper) extends BusTester(c, c.io.bus, forceReadStep = true) {
  def write(value: UInt, mask: UInt = "b1111".U) = {
    busWrite(0x0.U, value, mask)
  }
  def read() : BigInt = {
    busRead(0x0.U)
  }
  def expectRead(v: BigInt) = {
    val r = busRead(0.U)
    expect(r == v, s"$v == $r")
  }
  def expectRead(exp: String) : Unit = {
    expectRead(exp.U.litValue)
  }

  val bitMask = (1l << 10l) - 1l
  println(s"$bitMask")

  step(5)

  // Initial conditions
  expect(c.reg.io.value.getWidth == 10, "Expected output width")
  expect(c.io.value, "b0110100011".U)
  expect(c.io.out, "b0110100011".U)
  expectRead("b0110100011")

  // Longer write
  write("b111101011101111".U)
  expectRead("b1011101111")
  expect(c.io.value, "b1011101111".U)
  expect(c.io.out, "b1011101111".U)
  step(2)

  // Masked write
  write(0x8a7b6c5dl.U)
  write(0x12347891l.U, "b0010".U)
  expectRead(0x8a7b785dl & bitMask)
  write(0x00000000l.U, "b0001".U)
  expectRead(0x8a7b7800l & bitMask)
  step(5)
}


class TimerRegisterTester(c: TimerRegister) extends BetterPeekPokeTester(c) {
  expect(c.io.bus.rdata, 0)
  expect(c.io.bus.ready, true)
  for (i <- 0 to 1000) {
    nSteps(c.divider) {
      expect(c.io.bus.rdata, i)
    }
  }
}


class AcknowledgeRegisterTester(c: AcknowledgeRegister) extends RegisterTester(c, c.io.bus) {

  def setInput(idx: Int, value: Boolean) = poke(c.io.inputs(idx), value)

  for (i <- 0 until c.width) {
    setInput(i, false)
  }

  expectValue("b00000".U)
  step(5)
  setInput(0, true)
  step(1)
  setInput(0, false)
  expectValue("b00001".U)
  write("b10101".U)
  expectValue("b00000".U)
  step(10)

  expectValue("b00000".U)
  setInput(4, true)
  setInput(3, true)
  step(1)
  expectValue("b11000".U)
  step(3)
  expectValue("b11000".U)
  write("xffffffff".U, "b1110".U)
  expectValue("b11000".U)
  write("b01000".U, "b0001".U)
  expectValue("b10000".U)

  step(50)
}


class RegisterTests extends ChiselFlatSpec {
  val args = Array("--fint-write-vcd")
  "OutputRegister" should "output bits" in {
    iotesters.Driver.execute(args, () => new OutputRegisterWrapper) {
      c => new OutputRegisterTester(c)
    } should be (true)
  }
  "TimerRegister" should "count" in {
    iotesters.Driver.execute(args, () => new TimerRegister(11)) {
      c => new TimerRegisterTester(c)
    } should be (true)
  }
  "AcknowledgeRegister" should "work" in {
    iotesters.Driver.execute(args, () => new AcknowledgeRegister(5)) {
      c => new AcknowledgeRegisterTester(c)
    } should be (true)
  }
}
