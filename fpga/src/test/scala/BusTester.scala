
package gfc

import chisel3._
import chisel3.iotesters.{PeekPokeTester}

abstract class BusTester[+T <: Module](val c: T, val bus: MemoryBus, val readTimeout: Int = -1)
    extends BetterPeekPokeTester(c) {

  poke(bus.valid, false.B)
  poke(bus.addr, 0x00000000.U)
  poke(bus.wdata, 0x00000000.U)
  poke(bus.wstrb, "b0000".U)

  def setupBusWrite(address: UInt, value: UInt, mask: UInt = "b1111".U) = {
    poke(bus.valid, true.B)
    poke(bus.addr, address)
    poke(bus.wdata, value)
    poke(bus.wstrb, mask)
  }

  def setupBusRead(address: UInt) = {
    poke(bus.valid, true.B)
    poke(bus.addr, address)
    poke(bus.wstrb, 0.U)
  }

  def setupBusIdle() = {
    poke(bus.addr, 0.U)
    poke(bus.wstrb, 0.U)
    poke(bus.valid, false.B)
  }

  def busWrite(address: UInt, value: UInt, mask: UInt = "b1111".U) = {
    setupBusWrite(address, value, mask)
    step(1)
    setupBusIdle()
  }

  def busRead(address: UInt) : BigInt = {
    setupBusRead(address)
    stepWhile(peek(bus.ready) == 0, timeout=readTimeout) {}
    setupBusIdle()
    return peek(bus.rdata)
  }
}
