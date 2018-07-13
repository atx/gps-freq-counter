
package gfc

import chisel3._


class UARTTXTester(c: UART) extends BetterPeekPokeTester(c) {
  val bus = new gfc.test.BusHelper(this, c.io.bus)

  def readTxEmpty() : BigInt = {
    peek(c.io.status.txEmpty)
  }
  def writeTx(v: BigInt) = {
    bus.write(0x0.U, v.U, "b0001".U)
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
  val exs = List(0xaa, 0x6d, 0x41, 0xaa)
  val iter = exs.iterator
  writeTx(iter.next)
  stepWhile(readTxEmpty() == 0, c.divider * 2) {}
  writeTx(iter.next)

  for (ex <- exs) {
    val data = rxByte()
    expect(data == ex, s"received data ($data == $ex)")

    stepWhile(readTxEmpty() == 0, c.divider * 2) {}
    if (iter.hasNext) {
      writeTx(iter.next)
    }
  }

  stepWhile(readTxEmpty() == 0, c.divider * 2) {}
  writeTx(exs(2))

  step(1000)
}

class UARTRXTester(c: UART) extends BetterPeekPokeTester(c) {

  def readRxFull() : BigInt = peek(c.io.status.rxFull)
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
      var gotflag = false
      for (n <- 0 to 9) {
        val bit = (((dtxExtended >> n) & 0x1) == 1)
        poke(c.io.uart.rx, bit.B)

        nSteps(baudRate) {
          if (peek(c.io.status.rxFull) == 1) {
            expect(n == 8, s"rxFull flag after bit $n")
            gotflag = true
          }
        }
      }
      expect(gotflag, s"rxFull at the end")

      val dataRx = readRx()
      expect(dataRx == dataByte, s"read data ($dataRx == $dataByte)")
      step(baudRate)
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
