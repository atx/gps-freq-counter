
package gfc

import chisel3._


class UARTTXTester(c: UART) extends BusTester(c, c.io.bus) {

  def readStatus() : BigInt = {
    (busRead(0x0.U) >> 16) & 0xff
  }
  def writeTx(v: BigInt) = {
    busWrite(0x0.U, v.U, "b0001".U)
  }

  def rxByte() = {
    val baudRate = c.divider * c.perBit
    stepWhile(peek(c.io.uart.tx) == 1, baudRate * 2) {}
    // Start bit
    nSteps(baudRate) {
      expect(c.io.uart.tx, false.B, "start bit value")
    }
    var ret = 0x00
    for (n <- 0 to 7) {
      val bit = peek(c.io.uart.tx).toInt
      nSteps(baudRate) {
        expect(c.io.uart.tx, (bit == 1).B, s"bit $n should be constant")
      }
      ret = (bit << 7) | (ret >> 1)
    }
    nSteps(baudRate) {
      expect(c.io.uart.tx, true.B, "stop bit value")
    }
    ret
  }

  // Test single byte transmit
  step(10)
  expect(c.io.uart.tx, true.B)
  val exp = 0x41
  writeTx(exp)
  step(10)
  val data = rxByte()
  expect(data == exp, s"received data ($data == $exp)")
  nSteps(500) {
    expect(c.io.uart.tx, true.B)
  }

  // Multiple bytes back to back
  val exs = List(0xaa, 0x6d)
  writeTx(exs(0))
  stepWhile((readStatus() & 0x01) == 1, c.divider * 2) {}
  writeTx(exs(1))
  for (ex <- exs) {
    val data = rxByte()
    expect(data == ex, s"received data ($data == $ex)")
  }

  step(1000)
}

class UARTRXTester(c: UART) extends BusTester(c, c.io.bus, readTimeout=0) {

  def readRxFull() : BigInt = {
    (peek(c.io.bus.rdata) >> 17) & 0x1
  }
  def readRx() : BigInt = {
    (peek(c.io.bus.rdata) >> 8) & 0xff
  }

  poke(c.io.uart.rx, true.B)

  nSteps(30) {
    expect(readRxFull() == 0x0, "rxFull flag should be 0 on reset")
  }

  val baudRate = c.divider * c.perBit

  val testCases = List(
    (baudRate * 2.0,
      List(0x2d)),
    (baudRate * 0.1,
      List(0x00)),
    (baudRate * 0.1,
      List(0x7e)),
    (baudRate * 0.3,
      List(0x00, 0x00, 0xff, 0xff, 0x00, 0x00)),
    (baudRate * 4.9,
      List(0x12, 0x34, 0x56, 0xde, 0xad, 0xbe, 0xef, 0x00, 0xaa, 0x55))
    )

  for ((offsetTime, data) <- testCases) {
    step(offsetTime.toInt)

    for (dataByte <- data) {
      val dtxExtended = (1 << 9) | (dataByte << 1) | 0
      for (n <- 0 to 9) {
        val bit = (((dtxExtended >> n) & 0x1) == 1)
        poke(c.io.uart.rx, bit.B)
        if (n != 9) {  // The data should appear before the stop bit
          step(baudRate)
        }
      }

      expect(readRxFull() == 1, s"rxFull flag should be set at this point")
      val dataRx = readRx()
      expect(dataRx == dataByte, s"read data ($dataRx == $dataByte)")

      // Clear the rxFull flag by doing a proper single cycle read
      setupBusRead(0x0.U)
      step(1)
      expect(readRxFull() == 0, s"rxFull flag should be cleared after a read bus cycle")
      setupBusIdle()
      step(baudRate - 1)
    }
    poke(c.io.uart.rx, true.B)
  }

  step(1000)
}


class UARTTests extends GFCSpec {
  val dutGen = () => new UART(divider = 32, perBit = 15)
  should("transmit bytes", dutGen, new UARTTXTester(_))
  should("receive bytes", dutGen, new UARTRXTester(_))
}
