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

/**
  * Minimal DBUS-like interface used by MatrixCore.
  *
  * The parent (LSU) arbitrates this onto the real `DBusIO`.
  */
class MatrixMemIO(dataBits: Int) extends Bundle {
  val req = Decoupled(new Bundle {
    val pc = UInt(32.W)
    val write = Bool()
    val addr = UInt(32.W)
    val size = UInt((log2Ceil(dataBits / 8) + 1).W) // match DBusIO.size width
    val wdata = UInt(dataBits.W)
    val wmask = UInt((dataBits / 8).W)
  })
  val resp = Flipped(Valid(new MatrixMemResp(dataBits)))
}

class MatrixCoreIO(dataBits: Int) extends Bundle {
  val cmd = Flipped(Decoupled(new MatrixCmd))
  val mem = new MatrixMemIO(dataBits)
  val busy = Output(Bool())
}

