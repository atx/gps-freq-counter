
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec}


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


class RegisterTests extends ChiselFlatSpec {
  val args = Array("--fint-write-vcd")
  "OutputRegister" should "output bits" in {
    iotesters.Driver.execute(args, () => new OutputRegisterWrapper) {
      c => new OutputRegisterTester(c)
    } should be (true)
  }
}
