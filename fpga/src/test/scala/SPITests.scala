
package gfc

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester}


class SPITester(c: SPI) extends BusTester(c, c.io.bus) {
  // TODO: More comprehensive tests

  def readSPIByte() : Int = {
    var ret = 0x00
    for (_ <- 0 to 7) {
      while (peek(c.io.spi.clk) == 1) {
        step(1)
      }
      for (_ <- 0 to 4) {
        expect(c.io.spi.clk, false.B)
        step(1)
      }
      expect(c.io.spi.clk, true.B)
      ret = (ret << 1) | peek(c.io.spi.mosi).toInt
    }
    return ret
  }

  val expectedData = List(
    0x80, 0x70, 0x80, 0x80,
    0x01, 0xaa, 0x7f, 0x01,
    0x93
  )

  busWrite(0x4.U, 0x80807080l.U)
  busWrite(0x8.U, 0x017faa01l.U)
  busWrite(0xc.U, 0x12341293l.U)
  busWrite(0x0.U, 0x0901.U)

  for (expected <- expectedData) {
    var read = readSPIByte()
    println(s"$read $expected")
    assert(read == expected)
  }
  for (_ <- 0 to 20) {
    expect(c.io.spi.mosi, true.B)
    expect(c.io.spi.clk, true.B)
    step(1)
  }
}

class SPITests extends ChiselFlatSpec {
  "SPI" should "work" in {
    val args = Array("--fint-write-vcd")
    iotesters.Driver.execute(args, () => new SPI) {
      c => new SPITester(c)
    } should be (true)
  }
}
