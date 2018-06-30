
package gfc.test

import chisel3._
import chisel3.iotesters.{PeekPokeTester}


class BusHelper[+T <: Module](val tester: PeekPokeTester[T], val bus: gfc.MemoryBus,
    val forceReadStep: Boolean = true, val readTimeout: Int = 100) {
  // TODO: Drop BusTester and use this everywhere
  // It's more convenient to use literal UInts as Scala does not do binary int
  // literals
  // TODO: Look into how implicit type conversions work in scala

  tester.poke(bus.valid, 0)
  tester.poke(bus.addr, 0x00000000l)
  tester.poke(bus.wdata, 0x00000000l)
  tester.poke(bus.wstrb, 0x0)

  def setupWrite(address: BigInt, value: BigInt, mask: BigInt) {
    tester.poke(bus.valid, 1)
    tester.poke(bus.addr, address)
    tester.poke(bus.wdata, value)
    tester.poke(bus.wstrb, mask)
  }

  def setupWrite(address: UInt, value: UInt, mask: UInt = "b1111".U) {
    setupWrite(address.litValue, value.litValue, mask.litValue)
  }


  def setupRead(address: BigInt) {
    tester.poke(bus.valid, 1)
    tester.poke(bus.addr, address)
    tester.poke(bus.wstrb, 0x0)
  }

  def setupRead(address: UInt) {
    setupRead(address.litValue)
  }

  def setupIdle() {
    tester.poke(bus.addr, 0)
    tester.poke(bus.wstrb, 0)
    tester.poke(bus.valid, 0)
  }


  def write(address: BigInt, value: BigInt, mask: BigInt) {
    setupWrite(address, value, mask)
    tester.step(1)
    setupIdle()
  }

  def write(address: UInt, value: UInt, mask: UInt = "b1111".U) {
    write(address.litValue, value.litValue, mask.litValue)
  }


  def read(address: BigInt) : BigInt = {
    setupRead(address)
    if (forceReadStep) {
      tester.step(1)
    }
    var cnt = 0
    while (tester.peek(bus.ready) == 0) {
      tester.step(1)
      cnt += 1
      if (cnt == readTimeout) {
        tester.expect(false, f"Bus read timed out after $cnt steps!")
        tester.finish
      }
    }
    setupIdle()
    return tester.peek(bus.rdata)
  }

  def read(address: UInt) : BigInt = {
    read(address.litValue)
  }


  def reg() = { read(0x00000000l) }
  def reg(value: BigInt, mask: BigInt = 0xfl) = { write(0x00000000l, value, mask) }
}
