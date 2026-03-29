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
import coralnpu.{DBusIO, Parameters}

/**
  * MatrixCore:
  * - Sits next to LSU in SCore (optional via p.enableMatrix)
  * - Accepts [[MatrixQueuedCmd]] from Dispatch (op/pc + operand registers already read)
  * - Drives a real `DBusIO` master port
  */
class MatrixCore(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val inst = Flipped(Decoupled(new MatrixQueuedCmd))
    val dbus = new DBusIO(p)
    val dbusResp = Flipped(Valid(new MatrixMemResp(p.lsuDataBits)))
    val active = Output(Bool())
    /** Retire / fence ordering: pulses with PC when SET_C or MAC/MAC_ACC is architecturally done. */
    val matrixComplete = Output(Valid(UInt(32.W)))
  })

  val cBaseReg = RegInit(0.U(32.W))

  val q = Module(new Queue(new MatrixQueuedCmd, 8))
  q.io.enq <> io.inst

  val backend = Module(new MatrixBackend(p))

  backend.io.cmd.valid := false.B
  backend.io.cmd.bits := 0.U.asTypeOf(new MatrixCmd)

  val isSetC = q.io.deq.bits.op === MatrixOp.SET_C
  val canLaunch = q.io.deq.valid && (isSetC || backend.io.cmd.ready)

  q.io.deq.ready := canLaunch

  io.active := backend.io.busy || (q.io.count =/= 0.U)

  val setCThisCycle = canLaunch && isSetC
  io.matrixComplete.valid := setCThisCycle || backend.io.cmdComplete.valid
  io.matrixComplete.bits := Mux(setCThisCycle, q.io.deq.bits.pc, backend.io.cmdComplete.bits)

  when(canLaunch) {
    when(isSetC) {
      cBaseReg := q.io.deq.bits.rs1Data
    }.otherwise {
      backend.io.cmd.valid := true.B
      backend.io.cmd.bits.op := q.io.deq.bits.op
      backend.io.cmd.bits.pc := q.io.deq.bits.pc
      backend.io.cmd.bits.aBase := q.io.deq.bits.rs1Data
      backend.io.cmd.bits.bBase := q.io.deq.bits.rs2Data
      backend.io.cmd.bits.cBase := cBaseReg
    }
  }

  io.dbus.valid := backend.io.mem.req.valid
  io.dbus.write := backend.io.mem.req.bits.write
  io.dbus.pc := backend.io.mem.req.bits.pc
  io.dbus.addr := backend.io.mem.req.bits.addr
  io.dbus.adrx := backend.io.mem.req.bits.addr
  io.dbus.size := backend.io.mem.req.bits.size
  io.dbus.wdata := backend.io.mem.req.bits.wdata
  io.dbus.wmask := backend.io.mem.req.bits.wmask

  backend.io.mem.req.ready := io.dbus.ready
  backend.io.mem.resp := io.dbusResp
}
