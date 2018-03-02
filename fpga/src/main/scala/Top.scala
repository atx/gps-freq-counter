
package gfc

import chisel3._
import chisel3.util._

class Top extends Module {
  val io = IO(new Bundle {
  })

  val rv = Module(new PicoRV())

  val memory = SyncReadMem(2048, UInt(32.W))

  val write = rv.io.mem.valid && rv.io.mem.wstrb =/= 0.U
  val read = rv.io.mem.valid && rv.io.mem.wstrb === 0.U

  when (write) {
    memory.write(rv.io.mem.addr, rv.io.mem.wdata)
  } .otherwise {
    rv.io.mem.rdata := memory.read(rv.io.mem.addr)
  }
}
