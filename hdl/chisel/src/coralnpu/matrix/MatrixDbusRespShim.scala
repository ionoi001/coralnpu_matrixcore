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

/** Matrix-local DBUS response shim with configurable fixed read latency. */
class MatrixDbusRespShim(p: Parameters) extends Module {
  private val latency = p.matrixRespLatencyCycles
  require(latency >= 0, "matrixRespLatencyCycles must be >= 0")

  val io = IO(new Bundle {
    val useMatrix = Input(Bool())
    val dbusValid = Input(Bool())
    val dbusReady = Input(Bool())
    val dbusWrite = Input(Bool())
    val dbusRdata = Input(UInt(p.lsuDataBits.W))
    val resp = Valid(new MatrixMemResp(p.lsuDataBits))
  })

  val readReqFire = io.useMatrix && io.dbusValid && io.dbusReady && !io.dbusWrite
  io.resp.valid := (if (latency == 0) {
    readReqFire
  } else {
    ShiftRegister(readReqFire, latency)
  })
  io.resp.bits.rdata := io.dbusRdata
  io.resp.bits.error := false.B
}
