
package gfc

import chisel3._
import chisel3.util._

class UARTBundle extends Bundle {
  val rx = Input(Bool())
  val tx = Output(Bool())
}

class UART(val divider: Int = 32, val perBit: Int = 16) extends Module {
  require(perBit >= 8)
  val io = IO(new Bundle {
    val bus = Flipped(new MemoryBus)
    val uart = new UARTBundle
  })

  val writeBitCounter = Reg(UInt(4.W), init=0.U)
  val txData = Reg(UInt(8.W), init=0.U)
  val txDataR = Reg(UInt(8.W), init=0.U)
  val txFull = RegInit(false.B)
  val baudWriteCntr = Counter(perBit)
  val baseWriteCntr = Counter(divider)

  val receiveBitCounter = Reg(UInt(4.W), init=0.U)
  val startBitCounter = Reg(UInt(log2Up(perBit / 2).W), init=0.U)
  val rxData = Reg(UInt(8.W), init=0.U)
  val rxDataR = Reg(UInt(8.W))
  val rxFull = RegInit(false.B)
  val baudReadCntr = Counter(perBit)
  val baseReadCntr = Counter(divider)

  io.bus.rdata := Cat(rxFull, txFull, rxData, txData)
  io.bus.ready := true.B

  val rxSignal = Utils.synchronize(io.uart.rx)
  val txReg = RegInit(true.B)
  io.uart.tx := txReg

  when (receiveBitCounter === 0.U) {
    when (startBitCounter === 0.U) {  // Idle state
      when (Utils.fallingEdge(rxSignal)) {
        startBitCounter := 1.U
        baudReadCntr.value := 0.U
        baseReadCntr.value := 0.U
      }
    } .elsewhen(baseReadCntr.inc()) {
      when (!rxSignal) {
        startBitCounter := startBitCounter + 1.U
      } otherwise {
        startBitCounter := 0.U
      }
      when (startBitCounter >= (perBit / 2).U) {
        rxDataR := 0.U
        receiveBitCounter := 8.U
      }
    }
  } .elsewhen (receiveBitCounter > 0.U) {
    when (baseReadCntr.inc()) {
      when (baudReadCntr.inc()) {
        receiveBitCounter := receiveBitCounter - 1.U
        val rxDataRNext = (rxSignal << 7) | (rxDataR >> 1)
        when (receiveBitCounter === 1.U) {
          rxData := rxDataRNext
          rxFull := true.B
        } otherwise {
          rxDataR := rxDataRNext
        }
      }
    }
  }
  when (io.bus.valid && io.bus.wstrb === 0.U) {
    // The reader needs to take it from here
    rxFull := false.B
  }

  when (baseWriteCntr.inc()) {
    when (writeBitCounter === 0.U && txFull) {
      txDataR := txData
      txFull := false.B
      writeBitCounter := 10.U
      baudWriteCntr.value := 0.U
    } .elsewhen (writeBitCounter > 0.U && baudWriteCntr.inc()) {
      when (writeBitCounter === 10.U) {  // Start bit
        txReg := false.B
      } .elsewhen (writeBitCounter === 1.U) {  // Stop bit
        txReg := true.B
      } otherwise {  // Data
        txReg := txDataR(0)  // Low order bit first
        txDataR := txDataR >> 1.U
      }
      writeBitCounter := writeBitCounter - 1.U
    }
  }
  when (io.bus.valid && io.bus.wstrb(0) && !txFull) {
    // Our TX register is getting written to
    // It's the writer responsibility to check the txFull flag before writing
    // TODO: Maybe block the read signal instead?
    txData := io.bus.wdata(7, 0)
    txFull := true.B
  }
}
