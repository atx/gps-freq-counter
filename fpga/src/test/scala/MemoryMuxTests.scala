
package gfc

import scala.collection.immutable.ListMap
import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, Driver}

class MemoryMuxTestWrapper extends Module {
  val io = IO(new Bundle {
    val master = Flipped(new MemoryBus)
    val slaves = Vec(5, Output(new MemoryBus))  // TODO: No idea why this doesn't work
  })

  val m1 = Module(new FillMemory("x12341234".U))
  val m2 = Module(new FillMemory("xdeadbeef".U))
  val m3 = Module(new FillMemory("xcafebabe".U))
  val m4 = Module(new FillMemory("x61747820".U))
  val m5 = Module(new FillMemory("xabcdabcd".U))
  var ms = Array(m1, m2, m3, m4, m5)

  for (i <- 0 until 5) {
    io.slaves(i).valid := ms(i).io.mem.valid
    io.slaves(i).instr := ms(i).io.mem.instr
    io.slaves(i).addr := ms(i).io.mem.addr
    io.slaves(i).wdata := ms(i).io.mem.wdata
    io.slaves(i).wstrb := ms(i).io.mem.wstrb
    io.slaves(i).rdata := ms(i).io.mem.rdata
    io.slaves(i).ready := ms(i).io.mem.ready
  }

  // Memory map
  // 0x10 000000 - m1
  // 0x2000 0000 - m2
  // 0x3111234 0 - m3
  // 0x3111238 0 - m4
  // 0xff00ff 00 - m5
  var prefixes = Array(
    0x10.U(8.W),
    0x2000.U(16.W),
    0x3111234.U(28.W),
    0x3111238.U(28.W),
    0xff00ff.U(24.W)
  )
  val mux = Module(new MemoryMux(prefixes))
  m1.io.mem.valid := mux.io.slaves(0).valid
  for ((m, i) <- (ms zip mux.io.slaves)) {
    i <> m.io.mem
  }

  io.master <> mux.io.master
}

class MemoryMuxReadTester(c: MemoryMuxTestWrapper) extends PeekPokeTester(c) {
  poke(c.io.master.valid, false.B)
  poke(c.io.master.instr, false.B)
  poke(c.io.master.addr, 0xff.U(32.W))
  poke(c.io.master.wdata, 0xff.U(32.W))
  poke(c.io.master.wstrb, 0x00.U(4.W))

  val expected = ListMap(
    "x10000000" -> (0, "x12341234", "x00000000"),
    "x10000004" -> (0, "x12341234", "x00000004"),
    "x10002000" -> (0, "x12341234", "x00002000"),

    "x20002100" -> (1, "xdeadbeef", "x00002100"),
    "x20002300" -> (1, "xdeadbeef", "x00002300"),
    "x20002440" -> (1, "xdeadbeef", "x00002440"),

    "x31112340" -> (2, "xcafebabe", "x00000000"),
    "x31112348" -> (2, "xcafebabe", "x00000008"),
    "x31112380" -> (3, "x61747820", "x00000000"),
    "x3111238b" -> (3, "x61747820", "x0000000b"),

    "xff00ff00" -> (4, "xabcdabcd", "x00000000"),
    "xff00ff12" -> (4, "xabcdabcd", "x00000012"),
    "xff00fff0" -> (4, "xabcdabcd", "x000000f0")
  )

  step(2)

  for (((addr, (idx, eval, eaddr))) <- expected) {
    poke(c.io.master.valid, true.B)
    poke(c.io.master.addr, addr.U)
    val instr = idx % 2 == 1
    poke(c.io.master.instr, instr.B)
    step(1)

    for (sio <- c.io.slaves) {
      expect(sio.valid, (c.io.slaves(idx) == sio).B)
    }
    val sio = c.io.slaves(idx)
    expect(sio.wstrb, 0.U)
    expect(sio.instr, instr.B)
    expect(sio.addr, eaddr.U)
    expect(c.io.master.rdata, eval.U)

    poke(c.io.master.valid, false.B)
    step(1)
    for (sio <- c.io.slaves) {
      expect(sio.valid, false.B)
    }
  }
}

class MemoryMuxTests extends ChiselFlatSpec {
  "MemoryMux" should "properly select slaves to read from" in {
    val args = Array("--fint-write-vcd")
    iotesters.Driver.execute(args, () => new MemoryMuxTestWrapper) {
      c => new MemoryMuxReadTester(c)
    } should be (true)
  }
}
