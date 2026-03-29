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

/** Dot(K) multiply-accumulate for int8 vectors into int32 accumulator. */
class DotKMac(val k: Int) extends Module {
  require(k > 0)
  val io = IO(new Bundle {
    val en = Input(Bool())
    val clear = Input(Bool())
    val a = Input(Vec(k, SInt(8.W)))
    val b = Input(Vec(k, SInt(8.W)))
    val acc = Output(SInt(32.W))
  })

  val prods = Wire(Vec(k, SInt(32.W)))
  for (kk <- 0 until k) {
    prods(kk) := (io.a(kk) * io.b(kk)).asSInt.pad(32)
  }
  val sum = prods.reduceTree(_ +& _).asSInt

  val accReg = RegInit(0.S(32.W))
  when(io.en) {
    val base = Mux(io.clear, 0.S(32.W), accReg)
    accReg := (base +& sum).asSInt
  }
  io.acc := accReg
}

/**
  * MatrixEngine (outer-product broadcast, no systolic):
  *
  * Broadcast structure:
  * - `aRow(i)` is shared across all columns j
  * - `bCol(j)` is shared across all rows i
  *
  * Each accepted command performs one accumulation step:
  *   acc[i,j] := (clear ? 0 : acc[i,j]) + sum_{kk=0..K-1} aRow[i,kk] * bCol[j,kk]
  */
class MatrixEngine(val m: Int, val n: Int, val k: Int) extends Module {
  require(m > 0 && n > 0 && k > 0)

  val io = IO(new Bundle {
    val cmd = Flipped(Decoupled(new Bundle {
      val clear = Bool()
      val aRow = Vec(m, Vec(k, SInt(8.W)))
      val bCol = Vec(n, Vec(k, SInt(8.W)))
    }))
    val acc = Output(Vec(m, Vec(n, SInt(32.W))))
    val done = Output(Bool()) // 1-cycle pulse after cmd.fire
  })

  val macs = Seq.fill(m, n)(Module(new DotKMac(k)))
  val accW = Wire(Vec(m, Vec(n, SInt(32.W))))
  for (i <- 0 until m) {
    for (j <- 0 until n) {
      macs(i)(j).io.en := io.cmd.fire
      macs(i)(j).io.clear := io.cmd.fire && io.cmd.bits.clear
      macs(i)(j).io.a := io.cmd.bits.aRow(i)
      macs(i)(j).io.b := io.cmd.bits.bCol(j)
      accW(i)(j) := macs(i)(j).io.acc
    }
  }
  io.acc := accW
  io.done := RegNext(io.cmd.fire, false.B)
  io.cmd.ready := true.B
}
