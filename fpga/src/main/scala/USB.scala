
package gfc.usb

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog


object Protocol {
  object PID {
    val SETUP = "b00101101".U
    val DATA0 = "b11000011".U
    val DATA1 = "b01001011".U
    val ACK =   "b11010010".U
    val NAK =   "b01011010".U
    val IN =    "b01101001".U
    val OUT =   "b11100001".U
  }
}


protected class DiffPair extends Bundle {
  val dp = Bool()
  val dm = Bool()
}


protected class DiffDecoded extends Bundle {
  val j = Bool()
  val k = Bool()
  val jk = Bool()
  val se0 = Bool()
  val se1 = Bool()
}


protected class BitStream extends Bundle {
  val bit = Bool()
  val valid = Bool()
  val se0 = Bool()
}


protected class ByteStream extends Bundle {
  val byte = UInt(8.W)
  val valid = Bool()
  val eop = Bool()
}


protected class AckByteStream extends Bundle {
  val byte = Output(UInt(8.W))
  val eop = Output(Bool())
  val ack = Input(Bool())
}


class USBBundle extends Bundle {
  val dp = Analog(1.W)
  val dm = Analog(1.W)
}


class AnalogWrapper extends Module {
  val io = IO(new Bundle {
    val usb = new USBBundle
    val out = Output(new DiffPair)
    val in = Input(new DiffPair)
    val drive = Input(Bool())
  })

  val wdp = Module(new gfc.InoutWrapper)
  val wdm = Module(new gfc.InoutWrapper)

  for (w <- List(wdp, wdm)) {
    w.io.clock := clock
    w.io.reset := reset
    w.io.d.oe := io.drive
  }

  io.usb.dp <> wdp.io.d.pin
  io.usb.dm <> wdm.io.d.pin

  // Don't listen to ourselves
  when (!RegNext(io.drive)) {
    io.out.dp := wdp.io.d.read
    io.out.dm := wdm.io.d.read
  } .otherwise {
    // Note: This assumes low-speed!
    io.out.dp := false.B
    io.out.dm := true.B
  }

  wdp.io.d.write := io.in.dp
  wdm.io.d.write := io.in.dm
}


class Synchronizer(val cyclesPerBit: Int) extends Module {
  require(cyclesPerBit % 2 == 0)

  val io = IO(new Bundle {
    val usb = Input(new DiffPair)

    val stream = Output(new BitStream)
  })

  def decode(c: DiffPair) : DiffDecoded = {
    val d = Wire(new DiffDecoded())
    d.j := c.dm && !c.dp
    d.k := !c.dm && c.dp
    d.jk := d.j || d.k
    d.se0 := !c.dm && !c.dp
    d.se1 := c.dm && c.dp  // EVIL!
    return d
  }

  val buff = Reg(new BitStream)
  io.stream := buff
  buff.valid := false.B
  buff.se0 := false.B

  val bitCntr = Counter(cyclesPerBit)

  val prev = Reg(new DiffPair)

  when(bitCntr.inc()) {
    prev := io.usb

    val pd = decode(prev)
    val nd = decode(io.usb)

    when (pd.jk && nd.jk) {
      buff.valid := true.B
      buff.bit := pd.k && nd.k || pd.j && nd.j
    } .elsewhen (nd.se0) {
      buff.se0 := true.B
    }
  }
  when (RegNext(io.usb.toBits) =/= io.usb.toBits) {
    // Higher priority than the .inc() above. This should not collide
    // often though
    bitCntr.value := (cyclesPerBit / 2).U
  }
}


class Receiver extends Module {
  val io = IO(new Bundle {
    val stream = Input(new BitStream)
    val bytes = Output(new ByteStream)

    val reset = Output(Bool())
  })

  // TODO: Proper state machine here
  val sIdle :: sRx :: sWaitForEop :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val bitCnt = Reg(UInt(log2Ceil(7).W))
  val buffer = RegInit(255.U(8.W))
  val stuff = Reg(UInt(log2Ceil(6).W))

  val nextBuffer = ((buffer << 1.U) | io.stream.bit)(7, 0)

  val oValid = RegInit(false.B)
  val oByte = Reg(UInt(8.W))
  io.bytes.valid := oValid
  io.bytes.byte := Reverse(oByte)
  io.bytes.eop := state === sIdle

  oValid := false.B

  // Off-band reset handling
  val resetMin = 8.U  // This is a bit longer than strictly necessary
  val resetCnt = RegInit(resetMin)
  io.reset := resetCnt === 0.U
  when (io.stream.se0) {
    when (resetCnt =/= 0.U) {
      resetCnt := resetCnt - 1.U
    }
  } .elsewhen (io.stream.valid) {
    resetCnt := resetMin
  }

  switch (state) {
    is (sIdle) {
      when (io.stream.valid) {
        buffer := nextBuffer
        when (nextBuffer === "b00000001".U) {
          bitCnt := 0.U
          stuff := 1.U
          state := sRx
        }
      }
    }
    is (sRx) {
      when (io.stream.valid) {
        when (stuff === 6.U) {
          // Drop the stuff bit
          stuff := 0.U
        } otherwise {
          buffer := nextBuffer
          stuff := Mux(io.stream.bit, stuff + 1.U, 0.U)

          when (bitCnt === 7.U) {
            bitCnt := 0.U
            oValid := true.B
            oByte := nextBuffer
          } otherwise {
            bitCnt := bitCnt + 1.U
          }
        }
      } .elsewhen (io.stream.se0) {
        state := sWaitForEop
      }
    }
    is (sWaitForEop) {
      // Wait until some "normal" (idle) bit arrives
      when (io.stream.valid) {
        state := sIdle
      }
    }
  }
}


class Transmitter(val cyclesPerBit: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new AckByteStream)
    val usb = Output(new DiffPair)
    val drive = Output(Bool())
  })

  val k = Wire(new DiffPair)
  k.dp := true.B
  k.dm := false.B
  val j = Wire(new DiffPair)
  j.dp := false.B
  j.dm := true.B
  val se0 = Wire(new DiffPair)
  se0.dp := false.B
  se0.dm := false.B

  val outReg = RegInit(j)
  io.usb := outReg

  def nextBit(b: Bool) {
    when (!b) {
      outReg.dp := !outReg.dp
      outReg.dm := !outReg.dm
    }
  }

  val sIdle :: sPreSync :: sSync :: sData :: sStuff :: sEop :: sLastStuff :: Nil = Enum(7)
  val state = RegInit(sIdle)

  val buffer = Reg(UInt(8.W))
  val bitCnt = Reg(UInt(3.W))
  val stuff = Reg(UInt(log2Ceil(6).W))
  val sampleCnt = Counter(cyclesPerBit)

  io.in.ack := (state === sData && bitCnt === 7.U) || state === sIdle
  io.drive := RegNext(state =/= sIdle && state =/= sPreSync)

  // TODO: Fix the Reverse mess...
  switch (state) {
    is (sIdle) {
      when (!io.in.eop) {
        buffer := Reverse(io.in.byte)
        state := sPreSync
        sampleCnt.value := 0.U
        bitCnt := 0.U
      }
    }
    is (sPreSync) {
      when (sampleCnt.inc()) {
        bitCnt := bitCnt + 1.U
        when (bitCnt === 4.U) {
          bitCnt := 0.U
          state := sSync
        }
      }
    }
    is (sSync) {
      when (sampleCnt.inc()) {
        bitCnt := bitCnt + 1.U
        nextBit(bitCnt === 7.U)
        when (bitCnt === 7.U) {
          bitCnt := 0.U
          state := sData
          stuff := 1.U  // The 1 bit sent counts as stuff bit
        }
      }
    }
    is (sStuff) {
      when (sampleCnt.inc()) {
        nextBit(false.B)
        state := sData
        stuff := 0.U
      }
    }
    is (sData) {
      when (sampleCnt.inc()) {
        nextBit(buffer(7))

        when (bitCnt === 7.U) {
          bitCnt := 0.U
          when (io.in.eop) {
            state := sEop
          } otherwise {
            buffer := Reverse(io.in.byte)
          }
        } otherwise {
          bitCnt := bitCnt + 1.U
          buffer := buffer << 1.U
        }

        when (buffer(7) === 1.U) {
          stuff := stuff + 1.U
          when (stuff === 5.U) {
            when (bitCnt === 7.U && io.in.eop) {
              state := sLastStuff
            } otherwise {
              state := sStuff
            }
          }
        } otherwise {
          stuff := 0.U
        }
      }
    }
    is (sLastStuff) {
      when (sampleCnt.inc()) {
        nextBit(false.B)
        state := sEop
      }
    }
    is (sEop) {
      when (sampleCnt.inc()) {
        bitCnt := bitCnt + 1.U
        when (bitCnt === 4.U) {
          state := sIdle
        }
        when (bitCnt >= 2.U) {
          outReg := j
        } otherwise {
          outReg := se0
        }
      }
    }
  }
}


class Peripheral extends Module {
  val io = IO(new Bundle {
    val bus = new Bundle {
      val reg = Flipped(new gfc.MemoryBus)
      val mem = Flipped(new gfc.MemoryBus)
    }
    val usb = new Bundle {
      val in = Input(new ByteStream)
      val out = new AckByteStream
    }
    val status = new Bundle {
      val rxDone = Output(Bool())
      val txEmpty = Output(Bool())
    }
  })

  val sIdle :: sRx :: sAck :: sNak :: sDropPreTx :: sTx :: sPostTx :: sDrop :: sDropNak :: Nil = Enum(9)
  val state = RegInit(sIdle)

  // We can send/receive packets up to 11 bytes long (12 with the sync byte, which don't pass along)
  val memLength = 12
  val rxCounter = RegInit(0.U(4.W))
  val txCounter = RegInit(0.U(4.W))
  val txPtr = RegInit(0.U(4.W))
  val rxMem = SyncReadMem(memLength, UInt(8.W))  // TODO: Why is this not getting synthesized as an SRAM?
  val txMem = Mem(memLength, UInt(8.W))
  val txReady = RegInit(false.B)
  val isFull = RegInit(false.B)

  io.bus.reg.rdata := RegNext(rxCounter)
  io.bus.reg.ready := RegNext(io.bus.reg.valid)

  io.bus.mem.ready := RegNext(io.bus.mem.valid)
  when (io.bus.mem.isWrite) {
    txMem(io.bus.mem.wordAddress) := io.bus.mem.wdata(7, 0)
    io.bus.mem.rdata := DontCare
  } .otherwise {
    io.bus.mem.rdata := rxMem.read(io.bus.mem.wordAddress)
  }

  io.status.txEmpty := !txReady
  io.status.rxDone := isFull

  val readBuff = Reg(UInt(8.W))
  io.usb.out.byte := readBuff
  readBuff := txMem(txPtr)

  val wToReg = io.bus.reg.valid && io.bus.reg.wstrb(0)
  val wToAckBit = io.bus.reg.wdata(7)

  when (wToReg && !wToAckBit && !isFull) {
    // TODO: Write during TX??
    txCounter := io.bus.reg.wdata(3, 0)
    txReady := true.B
  }

  when (wToReg && io.bus.reg.wdata(7) && wToAckBit) {
    isFull := false.B
  }

  io.usb.out.eop := true.B

  switch (state) {
    is (sIdle) {
      txPtr := 0.U
      when (io.usb.in.valid) {
        val pid = io.usb.in.byte
        when (pid === Protocol.PID.SETUP || pid === Protocol.PID.OUT) {
          state := sDrop
        } .elsewhen (pid === Protocol.PID.DATA0 || pid === Protocol.PID.DATA1) {
          when (isFull) {
            state := sDropNak
          } otherwise {
            state := sRx

            rxMem.write(0.U, io.usb.in.byte)
            rxCounter := 1.U
          }
        } .elsewhen (pid === Protocol.PID.IN) {
          state := sDropPreTx
        } .elsewhen (pid === Protocol.PID.ACK) {
          txReady := false.B
          state := sDrop
        } otherwise {
          state := sDrop  // ¯\_(ツ)_/¯
        }
      }
    }
    is (sDropPreTx) {
      when (io.usb.in.eop) {
        when (txReady) {
          state := sTx
        } otherwise {
          state := sNak
        }
      }
    }
    is (sTx) {
      io.usb.out.eop := false.B
      when (gfc.Utils.risingEdge(io.usb.out.ack)) {
        txPtr := txPtr + 1.U
        when (txPtr === txCounter - 1.U) {
          state := sPostTx
        }
      }
    }
    is (sPostTx) {
      when (io.usb.in.eop) {
        state := sIdle
      }
    }
    is (sDrop) {
      when (io.usb.in.eop) {
        state := sIdle
      }
    }
    is (sDropNak) {
      when (io.usb.in.eop) {
        state := sNak
      }
    }
    is (sRx) {
      when (io.usb.in.valid && rxCounter =/= memLength.U) {
        rxMem.write(rxCounter, io.usb.in.byte)
        rxCounter := rxCounter + 1.U
      }
      when (io.usb.in.eop) {
        state := sAck
        isFull := true.B
        // Whatever we are TX-ing is not likely to be valid after this anyway
        // Also beware: the code needs to flush its buffer too at this point -
        // executing RX handler before TX handler is a good idea
        txReady := false.B
      }
    }
    is (sNak) {
      io.usb.out.eop := false.B
      io.usb.out.byte := Protocol.PID.NAK
      when (gfc.Utils.risingEdge(io.usb.out.ack)) {
        state := sDrop
      }
    }
    is (sAck) {
      io.usb.out.eop := false.B
      io.usb.out.byte := Protocol.PID.ACK
      when (gfc.Utils.risingEdge(io.usb.out.ack)) {
        state := sDrop
      }
    }
  }
}
