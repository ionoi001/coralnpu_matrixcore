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
import coralnpu.Parameters

object MatrixOp extends ChiselEnum {
  val SET_C = Value
  val MAC = Value
  val MAC_ACC = Value
}

/**
  * Full matrix issue from Dispatch: opcode/PC plus operand data already read from
  * the scalar regfile (see DispatchV2 + SCore). MatrixCore does not use separate
  * `rs1`/`rs2` read ports.
  */
class MatrixQueuedCmd extends Bundle {
  val op = MatrixOp()
  val pc = UInt(32.W)
  val rs1Data = UInt(32.W)
  val rs2Data = UInt(32.W)
}

class MatrixCmd extends Bundle {
  val op = MatrixOp()
  val pc = UInt(32.W)
  val aBase = UInt(32.W)
  val bBase = UInt(32.W)
  val cBase = UInt(32.W)
}

class MatrixMemResp(dataBits: Int) extends Bundle {
  val rdata = UInt(dataBits.W)
  val error = Bool()
}

/** One DBus beat request from [[MatrixBackend]]; consumed by LSU when `p.enableMatrix`. */
class MatrixDbusReq(p: Parameters) extends Bundle {
  val pc = UInt(32.W)
  val write = Bool()
  val addr = UInt(32.W)
  val size = UInt(p.dbusSize.W)
  val wdata = UInt(p.lsuDataBits.W)
  val wmask = UInt((p.lsuDataBits / 8).W)
}

/**
  * Memory port between [[MatrixBackend]] / [[MatrixCore]] and LSU.
  *
  * LSU owns `DBusIO`; it muxes these requests onto the bus and returns read data one cycle
  * after each read handshake (same timing model as scalar dbus loads).
  */
class MatrixMemIO(p: Parameters) extends Bundle {
  val req = Decoupled(new MatrixDbusReq(p))
  val resp = Flipped(Valid(new MatrixMemResp(p.lsuDataBits)))
}

class MatrixCoreIO(p: Parameters) extends Bundle {
  val cmd = Flipped(Decoupled(new MatrixCmd))
  val mem = new MatrixMemIO(p)
  val busy = Output(Bool())
  /** One-cycle pulse when a MAC/MAC_ACC has finished all C writeback beats (PC of the op). */
  val cmdComplete = Output(Valid(UInt(32.W)))
}

