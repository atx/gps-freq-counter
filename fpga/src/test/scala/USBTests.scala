
package gfc.usb

import chisel3._
import chisel3.iotesters.{PeekPokeTester}


class SynchronizerTester(c: Synchronizer) extends PeekPokeTester(c) {
  val j = (0,1)
  val k = (1,0)
  val se0 = (0,0)
  // From sigrok-dumps usb_setup_usb_reset_and_setup_lowspeed.sr
  val data = List(
    k,j,k,j,k,j,k,k,  // Sync pattern
    k,j,j,j,k,k,j,k,  // PID: SETUP
    j,k,j,k,j,k,j,    // Address: 0
    k,j,k,j,          // Endpoint: 0
    k,k,j,k,j,        // CRC5: 0x02
    )
  val expected = List(
    0,0,0,0,0,0,0,1,   // Sync
    1,0,1,1,0,1,0,0,   // PID: Setup
    0,0,0,0,0,0,0,     // Address: 0
    0,0,0,0,           // Endpoint: 0
    0,1,0,0,0,         // CRC5: 0x02
    )

  def p(pair: Tuple2[Int, Int]) = { poke(c.io.usb.dp, pair._1); poke(c.io.usb.dm, pair._2) }
  assert(data.length == expected.length)

  p(se0)  // Reset
  step(c.cyclesPerBit * 10)
  p(j)  // Idle
  step(c.cyclesPerBit * 8 + 50)

  for ((s, exp) <- (data zip expected)) {
    p(s)
    // TODO: Simulate jitter?
    var ok = false
    for (_ <- 1 to c.cyclesPerBit) {
      expect(c.io.stream.se0, false)
      step(1)
      if (peek(c.io.stream.valid) == 1) {
        assert(!ok)
        expect(c.io.stream.bit, exp)
        ok = true
      }
    }
    assert(ok)
  }

  p(se0)
  var cnt = 0
  for (_ <- 1 to 2*c.cyclesPerBit) {
    expect(c.io.stream.valid, false)
    if (peek(c.io.stream.se0) == 1) {
      cnt += 1
    }
    step(1)
  }
  assert(cnt == 2)
  p(j)

  step(20*c.cyclesPerBit)
}


class ReceiverTester(c: Receiver) extends gfc.BetterPeekPokeTester(c) {
  // TODO: Test for stuff starting with sync
  // TODO: Test several chained stuffs
  // TODO: Test stuff at the end of a packet
  val data = List(
    1,1,1,1,0,1,
    0,0,0,0,0,0,0,1,   // Sync
    1,0,1,1,0,1,0,0,   // PID: Setup
    1,1,1,1,1,1,0,1,1,   // Stuffed zero
    1,1,1,1,0,0,1,0,0   // Another
    )
  val bytes = List(
    "b00101101",
    "b11111111",
    "b00101111"
    )

  poke(c.io.stream.se0, true.B)
  poke(c.io.stream.valid, false.B)
  poke(c.io.stream.bit, 0)
  step(20)
  poke(c.io.stream.se0, false.B)
  step(5)

  var rx = List[BigInt]()
  for (d <- data) {
    poke(c.io.stream.valid, true.B)
    poke(c.io.stream.bit, d)
    if (peek(c.io.bytes.valid) == 1) {
      rx = rx :+ peek(c.io.bytes.byte)
    }
    step(1)
    poke(c.io.stream.valid, false.B)
    nSteps(10) {
      if (peek(c.io.bytes.valid) == 1) {
        rx = rx :+ peek(c.io.bytes.byte)
      }
    }
  }

  for ((r, e) <- (rx zip bytes)) {
    expect(r == e.U.litValue, s"$r == $e (received == expected)")
  }

  for (_ <- 1 to 2) {
    poke(c.io.stream.se0, true.B)
    step(1)
    poke(c.io.stream.se0, false.B)
    step(10)
  }
  poke(c.io.stream.bit, true.B)
  poke(c.io.stream.valid, true.B)
  step(1)
  poke(c.io.stream.valid, false.B)
  step(10)

  step(100)
}


class TransmitterTester(c: Transmitter) extends gfc.BetterPeekPokeTester(c) {
  poke(c.io.in.eop, true.B)
  poke(c.io.in.byte, 0.U)
  step(c.cyclesPerBit * 10)
  val packets = List(
    List(
      0x4b, // PID: DATA1
      0x00, 0x0a, 0x7e, 0x48  // Data (includes a stuffed bit)
    ),
    List(
      0xc3, // PID: DATA0
      0x80, 0x06, 0x00, 0x01, 0x00, 0x00, 0x12, 0x00, 0xe0, 0xf4
    ),
  )

  for (packet <- packets) {
    poke(c.io.in.eop, false.B)
    for (byte <- packet) {
      stepWhile(peek(c.io.in.ack) == 0, c.cyclesPerBit * 20) {}
      poke(c.io.in.byte, byte)
      step(1)
      stepWhile(peek(c.io.in.ack) == 1, c.cyclesPerBit * 2) {}
    }
    stepWhile(peek(c.io.in.ack) == 0, c.cyclesPerBit * 8) {}
    poke(c.io.in.eop, true.B)
    stepWhile(peek(c.io.in.ack) == 1, c.cyclesPerBit * 24) {}
    stepWhile(peek(c.io.in.ack) == 0, c.cyclesPerBit * 8) {}
  }

  step(c.cyclesPerBit * 20)
}


class PeripheralRxTester(c: Peripheral) extends PeekPokeTester(c) {
  val breg = new gfc.test.BusHelper(this, c.io.bus.reg)
  val bmem = new gfc.test.BusHelper(this, c.io.bus.mem)

  poke(c.io.usb.out.ack, false.B)
  poke(c.io.usb.in.valid, false.B)
  poke(c.io.usb.in.byte, 0x00)
  poke(c.io.usb.in.eop, false.B)
  step(20)

  val packetData = List(
    Protocol.PID.DATA0.litValue.toInt,
    0xad, 0xbe, 0xef, 0x12, 0x34, 0x56, 0x78, 0x9a
    )

  for (byte <- packetData) {
    poke(c.io.usb.in.valid, true.B)
    poke(c.io.usb.in.byte, byte)
    expect(c.io.status.rxDone, false.B)
    step(1)
    poke(c.io.usb.in.valid, false.B)
    expect(c.io.status.rxDone, false.B)
    step(5)
  }

  poke(c.io.usb.in.eop, true.B)
  expect(c.io.status.rxDone, false.B)
  step(1)
  expect(c.io.usb.out.eop, false.B)
  expect(c.io.usb.out.byte, Protocol.PID.ACK)
  poke(c.io.usb.in.eop, false.B)
  step(5)
  expect(c.io.status.rxDone, true.B)
  step(1)

  val rlen = breg.reg() & 0x0f
  assert(rlen == packetData.length, f"Received length ${rlen} == ${packetData.length}")

  for ((byte, i) <- packetData.zipWithIndex) {
    val read = bmem.read(i * 4)
    assert(read == byte, f"Read back mem[$i] == $read == $byte")
  }

  step(10)
  poke(c.io.usb.out.ack, true.B)
  step(2)
  expect(c.io.usb.out.eop, true.B)

  step(20)

  step(200)
}


class PeripheralTxTester(c: Peripheral) extends PeekPokeTester(c) {
  // TODO: Expects! (maybe try calling sigrok-cli?)
  val breg = new gfc.test.BusHelper(this, c.io.bus.reg)
  val bmem = new gfc.test.BusHelper(this, c.io.bus.mem)

  poke(c.io.usb.in.valid, false.B)
  poke(c.io.usb.in.byte, 0x00)
  poke(c.io.usb.in.eop, false.B)
  poke(c.io.usb.out.ack, true.B)
  step(10)

  val inPacketData = List(
      Protocol.PID.IN.litValue.toInt,
      0x00, 0x10
    )
  val rspPacketData = List(
      0xde, 0xad, 0xbe, 0xef, 0x12, 0x34, 0x56, 0x78, 0x9a
    )

  for ((byte, i) <- rspPacketData.zipWithIndex) {
    bmem.write(i * 4, byte, "b0001".U)
  }
  step(5)
  breg.reg(rspPacketData.length)

  for (byte <- inPacketData) {
    poke(c.io.usb.in.valid, true.B)
    poke(c.io.usb.in.byte, byte)
    step(1)
    poke(c.io.usb.in.valid, false.B)
    step(5)
  }
  poke(c.io.usb.in.eop, true.B)
  step(1)

  for (byte <- rspPacketData) {
    poke(c.io.usb.out.ack, true.B)
    step(10)
    expect(c.io.usb.out.byte, byte)
    poke(c.io.usb.out.ack, false.B)
    step(20)
  }
  poke(c.io.usb.out.ack, true.B)
  step(10)
  expect(c.io.usb.out.eop, true.B)

  step(20)
}


class USBTests extends gfc.GFCSpec {
  should("Synchronize to incoming bit stream", () => new Synchronizer(cyclesPerBit = 64), new SynchronizerTester(_))
  should("Split incoming bit stream to packet bytes", () => new Receiver, new ReceiverTester(_))
  should("Transmit packet bits", () => new Transmitter(64), new TransmitterTester(_))
  should("Correctly handle receiving USB packets", () => new Peripheral, new PeripheralRxTester(_))
  should("Correctly handle transmitting USB packets", () => new Peripheral, new PeripheralTxTester(_))
}
