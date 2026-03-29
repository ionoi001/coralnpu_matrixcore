// Copyright 2026
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package coralnpu.matrix

import chisel3._
import chisel3.util._

class MatrixDmaReq(dataBits: Int) extends Bundle {
  val pc = UInt(32.W)
  val addr = UInt(32.W)
  val size = UInt((log2Ceil(dataBits / 8) + 1).W)
  val wdata = UInt(dataBits.W)
  val wmask = UInt((dataBits / 8).W)
}

/** Read-side DMA request generator. */
class MatrixLoadDma(dataBits: Int) extends Module {
  val io = IO(new Bundle {
    val reqValid = Input(Bool())
    val pc = Input(UInt(32.W))
    val addr = Input(UInt(32.W))
    val req = Decoupled(new MatrixDmaReq(dataBits))
    val fire = Output(Bool())
  })

  io.req.valid := io.reqValid
  io.req.bits.pc := io.pc
  io.req.bits.addr := io.addr
  io.req.bits.size := (dataBits / 8).U(io.req.bits.size.getWidth.W)
  io.req.bits.wdata := 0.U
  io.req.bits.wmask := 0.U
  io.fire := io.req.fire
}

/** Write-side DMA request generator. */
class MatrixStoreDma(dataBits: Int) extends Module {
  val io = IO(new Bundle {
    val reqValid = Input(Bool())
    val pc = Input(UInt(32.W))
    val addr = Input(UInt(32.W))
    val wdata = Input(UInt(dataBits.W))
    val wmask = Input(UInt((dataBits / 8).W))
    val req = Decoupled(new MatrixDmaReq(dataBits))
    val fire = Output(Bool())
  })

  io.req.valid := io.reqValid
  io.req.bits.pc := io.pc
  io.req.bits.addr := io.addr
  io.req.bits.size := (dataBits / 8).U(io.req.bits.size.getWidth.W)
  io.req.bits.wdata := io.wdata
  io.req.bits.wmask := io.wmask
  io.fire := io.req.fire
}

/**
  * Matrix DMA scheduler:
  * - Uses separate load/store DMA request generators
  * - Arbitrates between them with pressure hints from MatrixBackend
  * - Generates a single DBUS-like request stream
  * - Exposes fired read/write events back to backend
  */
class MatrixDmaScheduler(dataBits: Int) extends Module {
  val io = IO(new Bundle {
    val readReqValid = Input(Bool())
    val readPc = Input(UInt(32.W))
    val readAddr = Input(UInt(32.W))

    val writeReqValid = Input(Bool())
    val writePc = Input(UInt(32.W))
    val writeAddr = Input(UInt(32.W))
    val writeData = Input(UInt(dataBits.W))
    val writeMask = Input(UInt((dataBits / 8).W))
    val preferRead = Input(Bool())
    val preferWrite = Input(Bool())

    val mem = Decoupled(new Bundle {
      val pc = UInt(32.W)
      val write = Bool()
      val addr = UInt(32.W)
      val size = UInt((log2Ceil(dataBits / 8) + 1).W)
      val wdata = UInt(dataBits.W)
      val wmask = UInt((dataBits / 8).W)
    })

    val readFire = Output(Bool())
    val writeFire = Output(Bool())
  })

  val load = Module(new MatrixLoadDma(dataBits))
  val store = Module(new MatrixStoreDma(dataBits))

  load.io.reqValid := io.readReqValid
  load.io.pc := io.readPc
  load.io.addr := io.readAddr

  store.io.reqValid := io.writeReqValid
  store.io.pc := io.writePc
  store.io.addr := io.writeAddr
  store.io.wdata := io.writeData
  store.io.wmask := io.writeMask

  // Arbitration policy:
  // 1) exclusive pressure hints (preferRead XOR preferWrite) win outright
  // 2) when both ports want the bus and hints tie-break (or both hints false), use round-robin
  //
  // Important: the old formula `store && (!load || chooseWriteByHint || ...)` starves writes when
  // chooseReadByHint is true and both load/store are valid (pickWrite becomes `store && !load`).
  val rrPreferWrite = RegInit(false.B)
  val bothValid = load.io.req.valid && store.io.req.valid
  val chooseWriteByHint = io.preferWrite && !io.preferRead
  val chooseReadByHint = io.preferRead && !io.preferWrite
  val tieBreakRr = !chooseReadByHint && !chooseWriteByHint
  val pickWrite = store.io.req.valid && (
    !load.io.req.valid ||
      (chooseWriteByHint && !chooseReadByHint) ||
      (tieBreakRr && rrPreferWrite)
  )
  val pickRead = load.io.req.valid && (
    !store.io.req.valid ||
      (chooseReadByHint && !chooseWriteByHint) ||
      (tieBreakRr && !rrPreferWrite)
  )

  io.mem.valid := pickRead || pickWrite
  io.mem.bits.pc := Mux(pickWrite, store.io.req.bits.pc, load.io.req.bits.pc)
  io.mem.bits.write := pickWrite
  io.mem.bits.addr := Mux(pickWrite, store.io.req.bits.addr, load.io.req.bits.addr)
  io.mem.bits.size := Mux(pickWrite, store.io.req.bits.size, load.io.req.bits.size)
  io.mem.bits.wdata := Mux(pickWrite, store.io.req.bits.wdata, 0.U)
  io.mem.bits.wmask := Mux(pickWrite, store.io.req.bits.wmask, 0.U)

  load.io.req.ready := io.mem.ready && pickRead
  store.io.req.ready := io.mem.ready && pickWrite

  when(io.mem.fire && bothValid && tieBreakRr) {
    rrPreferWrite := !rrPreferWrite
  }

  io.readFire := load.io.fire
  io.writeFire := store.io.fire
}
