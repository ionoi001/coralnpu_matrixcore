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

/**
  * MatrixBackend:
  * - Accepts a MatrixCmd stream (already includes a/b/c base addresses)
  * - Uses local scratchpad buffers for A/B tiles (ping-pong banks)
  * - Issues memory requests through a DBUS-like port via integrated DMA-style
  *   load/store schedulers
  * - Runs MatrixEngine as broadcast outer-product MAC (parameterized MxNxK)
  * - Writes back the MxN int32 accumulator tile to memory
  *
  * Contract:
  * - Reuses the existing DBUS path.
  * - Uses full DBUS beat width (`dataBits/8`) for reads/writes.
  * - Writes int32 accumulators back to memory.
  * - Read data consumption is driven by `io.mem.resp.valid`; no fixed-cycle
  *   assumption inside backend state transitions.
  * - Supports overlap: current C writeback interleaves with next A/B prefetch.
  */
class MatrixBackend(p: coralnpu.Parameters) extends Module {
  class MatrixReadCtx extends Bundle {
    val forCur = Bool()
    val readA = Bool()
    val beatIdx = UInt(readBeatIdxW.W)
    val toBuf1 = Bool()
  }

  private val dataBits = p.lsuDataBits
  require(dataBits % 8 == 0)
  private val beatBytes = dataBits / 8
  private val m = p.matrixM
  private val n = p.matrixN
  private val kDim = p.matrixK
  private val aBytes = m * kDim
  private val bBytes = kDim * n
  private val cBytes = m * n * 4
  require(beatBytes % 4 == 0, "DBUS beat bytes must be word-aligned")

  private def ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
  private val aReadBeats = ceilDiv(aBytes, beatBytes)
  private val bReadBeats = ceilDiv(bBytes, beatBytes)
  private val cWriteBeats = ceilDiv(cBytes, beatBytes)
  private val readBeatIdxW = math.max(1, log2Ceil(math.max(aReadBeats, bReadBeats)))
  private val writeBeatIdxW = math.max(1, log2Ceil(cWriteBeats))
  private val mn = m * n
  private val accIdxW = math.max(1, log2Ceil(math.max(mn, 1)))
  private val aIdxW = math.max(1, log2Ceil(math.max(aBytes, 1)))
  private val bIdxW = math.max(1, log2Ceil(math.max(bBytes, 1)))

  val io = IO(new MatrixCoreIO(dataBits))

  val cmdQ = Module(new Queue(new MatrixCmd, 8))
  cmdQ.io.enq <> io.cmd

  val aBuf0 = RegInit(VecInit.fill(aBytes)(0.S(8.W)))
  val bBuf0 = RegInit(VecInit.fill(bBytes)(0.S(8.W)))
  val aBuf1 = RegInit(VecInit.fill(aBytes)(0.S(8.W)))
  val bBuf1 = RegInit(VecInit.fill(bBytes)(0.S(8.W)))
  val curSel = RegInit(false.B)

  // Current command context.
  val curValid = RegInit(false.B)
  val curPrefetched = RegInit(false.B)
  val curOp = RegInit(MatrixOp.MAC)
  val curPc = RegInit(0.U(32.W))
  val curABase = RegInit(0.U(32.W))
  val curBBase = RegInit(0.U(32.W))
  val curCBase = RegInit(0.U(32.W))
  val curReadA = RegInit(true.B)
  val curReadBeatIdx = RegInit(0.U(readBeatIdxW.W))

  // Next command context.
  val nextValid = RegInit(false.B)
  val nextPrefetched = RegInit(false.B)
  val nextOp = RegInit(MatrixOp.MAC)
  val nextPc = RegInit(0.U(32.W))
  val nextABase = RegInit(0.U(32.W))
  val nextBBase = RegInit(0.U(32.W))
  val nextCBase = RegInit(0.U(32.W))
  val nextReadA = RegInit(true.B)
  val nextReadBeatIdx = RegInit(0.U(readBeatIdxW.W))

  val writeActive = RegInit(false.B)
  val writeBeatIdx = RegInit(0.U(writeBeatIdxW.W))

  val engine = Module(new MatrixEngine(m = m, n = n, k = kDim))
  engine.io.cmd.bits.clear := (curOp === MatrixOp.MAC)
  for (i <- 0 until m) {
    for (kk <- 0 until kDim) {
      engine.io.cmd.bits.aRow(i)(kk) := Mux(curSel, aBuf1(i * kDim + kk), aBuf0(i * kDim + kk))
    }
  }
  for (j <- 0 until n) {
    for (kk <- 0 until kDim) {
      engine.io.cmd.bits.bCol(j)(kk) := Mux(curSel, bBuf1(kk * n + j), bBuf0(kk * n + j))
    }
  }

  val resultBuf = RegInit(VecInit.fill(m * n)(0.S(32.W)))
  val resultValid = RegInit(false.B)
  val computeInFlight = RegInit(false.B)

  val computePending =
    curValid && curPrefetched && !writeActive && !computeInFlight && !resultValid

  engine.io.cmd.valid := computePending
  when(computePending) { assert(engine.io.cmd.ready) }
  when(engine.io.cmd.fire) {
    computeInFlight := true.B
  }

  val writeBytes = Wire(Vec(beatBytes, UInt(8.W)))
  val writeMask = Wire(Vec(beatBytes, Bool()))
  val writeBaseByte = writeBeatIdx * beatBytes.U
  for (b <- 0 until beatBytes) {
    val globalByte = writeBaseByte + b.U
    val inRange = globalByte < cBytes.U
    val elemIdxRaw = globalByte >> 2
    val elemIdx = Mux(elemIdxRaw < mn.U, elemIdxRaw(accIdxW - 1, 0), 0.U(accIdxW.W))
    val word = resultBuf(elemIdx).asUInt
    val byteSel = globalByte(1, 0)
    val selectedByte = MuxLookup(byteSel, 0.U(8.W))(Seq(
      0.U -> word(7, 0),
      1.U -> word(15, 8),
      2.U -> word(23, 16),
      3.U -> word(31, 24),
    ))
    writeBytes(b) := Mux(inRange, selectedByte, 0.U)
    writeMask(b) := inRange
  }

  val canAcceptCur = !curValid
  val canAcceptNext = curValid && !nextValid
  val canAcceptFromQ = cmdQ.io.deq.valid && (canAcceptCur || canAcceptNext)
  cmdQ.io.deq.ready := canAcceptFromQ
  when(canAcceptFromQ) {
    when(canAcceptCur) {
      curValid := true.B
      curPrefetched := false.B
      curOp := cmdQ.io.deq.bits.op
      curPc := cmdQ.io.deq.bits.pc
      curABase := cmdQ.io.deq.bits.aBase
      curBBase := cmdQ.io.deq.bits.bBase
      curCBase := cmdQ.io.deq.bits.cBase
      curReadA := true.B
      curReadBeatIdx := 0.U
    }.elsewhen(canAcceptNext) {
      nextValid := true.B
      nextPrefetched := false.B
      nextOp := cmdQ.io.deq.bits.op
      nextPc := cmdQ.io.deq.bits.pc
      nextABase := cmdQ.io.deq.bits.aBase
      nextBBase := cmdQ.io.deq.bits.bBase
      nextCBase := cmdQ.io.deq.bits.cBase
      nextReadA := true.B
      nextReadBeatIdx := 0.U
    }
  }
  
  val pendingRead = RegInit(false.B)
  val pendingCtx = Reg(new MatrixReadCtx)
  // Keep core-side arbitration pinned while any read response is pending.
  io.busy := curValid || nextValid || writeActive || computeInFlight || resultValid || pendingRead

  val prefetchCur = curValid && !curPrefetched
  val prefetchNext = !prefetchCur && nextValid && !nextPrefetched
  val prefetchActive = prefetchCur || prefetchNext
  val prefetchReadA = Mux(prefetchCur, curReadA, nextReadA)
  val prefetchBeatIdx = Mux(prefetchCur, curReadBeatIdx, nextReadBeatIdx)
  val prefetchABase = Mux(prefetchCur, curABase, nextABase)
  val prefetchBBase = Mux(prefetchCur, curBBase, nextBBase)
  val prefetchPc = Mux(prefetchCur, curPc, nextPc)
  val prefetchAddr = Mux(prefetchReadA,
    prefetchABase + (prefetchBeatIdx * beatBytes.U),
    prefetchBBase + (prefetchBeatIdx * beatBytes.U)
  )


  val readReqValid = prefetchActive && !pendingRead
  val writeReqValid = writeActive
  val lowPrefetchPressure = prefetchCur || (prefetchNext && nextReadA && nextReadBeatIdx === 0.U)
  val highWritePressure = writeActive && (!prefetchActive || (prefetchNext && !nextReadA))
  val dma = Module(new MatrixDmaScheduler(dataBits))
  dma.io.readReqValid := readReqValid
  dma.io.readPc := prefetchPc
  dma.io.readAddr := prefetchAddr
  dma.io.writeReqValid := writeReqValid
  dma.io.writePc := curPc
  dma.io.writeAddr := curCBase + (writeBeatIdx * beatBytes.U)
  dma.io.writeData := Cat(writeBytes.reverse)
  dma.io.writeMask := writeMask.asUInt
  dma.io.preferRead := lowPrefetchPressure
  dma.io.preferWrite := highWritePressure

  io.mem.req.valid := dma.io.mem.valid
  io.mem.req.bits := dma.io.mem.bits
  dma.io.mem.ready := io.mem.req.ready

  val memReadFire = dma.io.readFire
  val memWriteFire = dma.io.writeFire

  when(memReadFire) {
    pendingRead := true.B
    pendingCtx.forCur := prefetchCur
    pendingCtx.readA := prefetchReadA
    pendingCtx.beatIdx := prefetchBeatIdx
    pendingCtx.toBuf1 := Mux(prefetchCur, curSel, !curSel)
  }

  when(pendingRead && io.mem.resp.valid) {
    val rdata = io.mem.resp.bits.rdata
    val bytes = Wire(Vec(beatBytes, UInt(8.W)))
    for (bi <- 0 until beatBytes) {
      bytes(bi) := rdata(8 * (bi + 1) - 1, 8 * bi)
    }
    for (k <- 0 until beatBytes) {
      val idx = (pendingCtx.beatIdx * beatBytes.U) + k.U
      when(pendingCtx.readA) {
        when(idx < aBytes.U) {
          val idxA = idx(aIdxW - 1, 0)
          when(pendingCtx.toBuf1) { aBuf1(idxA) := bytes(k).asSInt }.otherwise { aBuf0(idxA) := bytes(k).asSInt }
        }
      }.otherwise {
        when(idx < bBytes.U) {
          val idxB = idx(bIdxW - 1, 0)
          when(pendingCtx.toBuf1) { bBuf1(idxB) := bytes(k).asSInt }.otherwise { bBuf0(idxB) := bytes(k).asSInt }
        }
      }
    }
    pendingRead := false.B

    val lastA = pendingCtx.readA && (pendingCtx.beatIdx === (aReadBeats - 1).U)
    val lastB = !pendingCtx.readA && (pendingCtx.beatIdx === (bReadBeats - 1).U)
    when(pendingCtx.forCur) {
      when(lastA) {
        curReadA := false.B
        curReadBeatIdx := 0.U
      }.elsewhen(lastB) {
        curPrefetched := true.B
      }.otherwise {
        curReadBeatIdx := curReadBeatIdx + 1.U
      }
    }.otherwise {
      when(lastA) {
        nextReadA := false.B
        nextReadBeatIdx := 0.U
      }.elsewhen(lastB) {
        nextPrefetched := true.B
      }.otherwise {
        nextReadBeatIdx := nextReadBeatIdx + 1.U
      }
    }
  }

  // Capture stable accumulator tile for writeback.
  when(engine.io.done) {
    for (i <- 0 until m) {
      for (j <- 0 until n) {
        resultBuf(i * n + j) := engine.io.acc(i)(j)
      }
    }
    computeInFlight := false.B
    resultValid := true.B
    writeActive := true.B
    writeBeatIdx := 0.U
  }

  when(memWriteFire) {
    when(writeBeatIdx === (cWriteBeats - 1).U) {
      writeActive := false.B
      resultValid := false.B
      curValid := false.B
      curPrefetched := false.B

      when(nextValid) {
        curValid := true.B
        curPrefetched := nextPrefetched
        curOp := nextOp
        curPc := nextPc
        curABase := nextABase
        curBBase := nextBBase
        curCBase := nextCBase
        curReadA := nextReadA
        curReadBeatIdx := nextReadBeatIdx
        nextValid := false.B
        nextPrefetched := false.B
        nextOp := MatrixOp.MAC
        nextReadA := true.B
        nextReadBeatIdx := 0.U
        curSel := !curSel
      }
    }.otherwise {
      writeBeatIdx := writeBeatIdx + 1.U
    }
  }
}
