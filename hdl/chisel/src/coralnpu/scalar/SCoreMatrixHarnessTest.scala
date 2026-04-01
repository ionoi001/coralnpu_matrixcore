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

package coralnpu

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec

/**
  * System-level tests: fetch -> decode -> dispatch -> regfile -> MatrixCore ->
  * MatrixBackend -> DBus (with MatrixDbusRespShim) -> memory model -> C writeback.
  *
  * Uses [[SCore]] with uncached fetch, matrix enabled, and small 2x2xK tiles for
  * fast simulation.
  *
  * Testbench must only drive [[coralnpu.SCore]] top-level **inputs**. In particular
  * `io.dm.scalar_rd` is [[chisel3.util.DecoupledIO]] flipped: `ready` is a DUT output.
  */
class SCoreMatrixHarnessSpec extends AnyFreeSpec with ChiselSim {

  /** Same configuration as [[coralnpu.CoralNpuMatrixCoreParameters]] / SV export targets. */
  private def mkParams(): Parameters = CoralNpuMatrixCoreParameters()

  private def lui(rd: Int, imm20: Int): Int = {
    val imm = imm20 & 0xfffff
    (imm << 12) | (rd << 7) | 0x37
  }

  /** SET_C: rs1 = C base; I-type-like, funct3=0, opcode 0x5B */
  private def instSetC(rs1: Int): Int = {
    (rs1 << 15) | (0 << 7) | 0x5b
  }

  /** MAC / MAC_ACC: R-type, funct3=001 / 010, opcode 0x5B */
  private def instMac(rs1: Int, rs2: Int): Int = {
    (rs2 << 20) | (rs1 << 15) | (1 << 12) | (0 << 7) | 0x5b
  }

  private def instMacAcc(rs1: Int, rs2: Int): Int = {
    (rs2 << 20) | (rs1 << 15) | (2 << 12) | (0 << 7) | 0x5b
  }

  private def instFenceI: Int = 0x0000100f

  /** addi rd, rs1, imm12 (I-type, funct3=000) */
  private def instAddi(rd: Int, rs1: Int, imm: Int): Int = {
    val imm12 = imm & 0xfff
    (imm12 << 20) | (rs1 << 15) | (0 << 12) | (rd << 7) | 0x13
  }

  /** lw rd, imm(rs1) */
  private def instLw(rd: Int, rs1: Int, imm: Int): Int = {
    val imm12 = imm & 0xfff
    (imm12 << 20) | (rs1 << 15) | (2 << 12) | (rd << 7) | 0x03
  }

  /** sw rs2, imm(rs1) */
  private def instSw(rs2: Int, rs1: Int, imm: Int): Int = {
    val imm12 = imm & 0xfff
    val imm31_25 = (imm12 >> 5) & 0x7f
    val imm4_0 = imm12 & 0x1f
    (imm31_25 << 25) | (rs2 << 20) | (rs1 << 15) | (2 << 12) | (imm4_0 << 7) | 0x23
  }

  /** jal x0, 0: infinite loop */
  private def instJalX0: Int = 0x0000006f

  private def packBytesLE(bytes: Seq[Int], widthBytes: Int): BigInt = {
    val padded = bytes ++ Seq.fill(widthBytes - bytes.length)(0)
    padded.zipWithIndex.map { case (b, i) => (BigInt(b & 0xff) << (8 * i)) }.reduce(_ | _)
  }

  private def int32sToBytesLE(words: Seq[Int]): Seq[Int] = {
    words.flatMap { w =>
      Seq(
        (w >>> 0) & 0xff,
        (w >>> 8) & 0xff,
        (w >>> 16) & 0xff,
        (w >>> 24) & 0xff,
      )
    }
  }

  /** 128b line indexed by fetch address (16-byte aligned). */
  private def imemLine(inst: Array[Int], basePc: Int, lineAddr: Int): BigInt = {
    val beatBytes = 16
    val wordsPerLine = beatBytes / 4
    val startIdx = (lineAddr - basePc) / 4
    var v = BigInt(0)
    for (i <- 0 until wordsPerLine) {
      val idx = startIdx + i
      val w = if (idx >= 0 && idx < inst.length) inst(idx) else instJalX0
      v |= (BigInt(w & 0xffffffffL) << (32 * i))
    }
    v
  }

  private def dmemLoad128(dmem: scala.collection.mutable.Map[BigInt, Int], addr: BigInt, beatBytes: Int): BigInt = {
    var v = BigInt(0)
    for (i <- 0 until beatBytes) {
      val b = BigInt(dmem.getOrElse(addr + i, 0))
      v |= (b & 0xff) << (8 * i)
    }
    v
  }

  /** Apply a DBus store beat to the byte-level memory model (scalar SW + matrix writeback). */
  private def dmemApplyWrite(
      dmem: scala.collection.mutable.Map[BigInt, Int],
      addr: BigInt,
      wdata: BigInt,
      wmask: BigInt,
      beatBytes: Int,
  ): Unit = {
    for (i <- 0 until beatBytes) {
      if (((wmask >> i) & 1) == 1) {
        val byte = ((wdata >> (8 * i)) & 0xff).toInt
        dmem(addr + i) = byte
      }
    }
  }

  private def applyReset(dut: SCore): Unit = {
    dut.reset.poke(true.B)
    dut.clock.step()
    dut.reset.poke(false.B)
    dut.clock.step()
  }

  private def tieOffMinimal(dut: SCore, p: Parameters): Unit = {
    dut.io.irq.poke(false.B)
    dut.io.dm.debug_req.poke(false.B)
    dut.io.dm.resume_req.poke(false.B)
    dut.io.dm.csr.valid.poke(false.B)
    dut.io.dm.csr_rs1.poke(0.U)
    // CoreDMIO.scalar_rd is Flipped(Decoupled): valid/bits are TB inputs; ready is DUT output.
    dut.io.dm.scalar_rd.valid.poke(false.B)
    dut.io.dm.scalar_rd.bits.addr.poke(0.U)
    dut.io.dm.scalar_rd.bits.data.poke(0.U)
    dut.io.dm.scalar_rs.idx.poke(0.U)
    dut.io.ebus.fault.valid.poke(false.B)
    dut.io.ebus.fault.bits.write.poke(false.B)
    dut.io.ebus.fault.bits.addr.poke(0.U)
    dut.io.ebus.fault.bits.epc.poke(0.U)
    dut.io.iflush.ready.poke(true.B)
    dut.io.dflush.ready.poke(true.B)
    dut.io.ibus.fault.valid.poke(false.B)
    dut.io.ibus.fault.bits.write.poke(false.B)
    dut.io.ibus.fault.bits.addr.poke(0.U)
    dut.io.ibus.fault.bits.epc.poke(0.U)
    for (i <- 0 until p.csrInCount) {
      dut.io.csr.in.value(i).poke(0.U)
    }
  }

  "Case1 SET_C + MAC writes expected C via full core path" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000

    val xA = 10
    val xB = 11
    val xC = 12

    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      instMac(xA, xB),
      instJalX0,
    )

    val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val cWords = Seq(50, 60, 114, 140)
    val expectedC = packBytesLE(int32sToBytesLE(cWords), beatBytes)

    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var sawCWrite = false
      var lastWriteAddr = BigInt(0)
      var lastWriteData = BigInt(0)

      for (_ <- 0 until 5000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        val dbusReadAddr = dut.io.dbus.addr.peek().litValue
        dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))

        dut.io.dbus.ready.poke(true.B)

        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
          if (dut.io.dbus.write.peek().litToBoolean) {
            val addr = dut.io.dbus.addr.peek().litValue
            val wdata = dut.io.dbus.wdata.peek().litValue
            if (addr == BigInt(cBase)) {
              sawCWrite = true
              lastWriteAddr = addr
              lastWriteData = wdata
            }
          }
        }

        dut.clock.step()
      }

      assert(sawCWrite, "expected matrix writeback to C base")
      assert(lastWriteAddr == BigInt(cBase), s"C write addr: 0x${lastWriteAddr.toString(16)}")
      assert(lastWriteData == expectedC, s"C wdata: got 0x${lastWriteData.toString(16)} exp 0x${expectedC.toString(16)}")
    }
  }

  "Case2 SET_C + MAC + MAC_ACC accumulates (program-level)" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000

    val xA = 10
    val xB = 11
    val xC = 12

    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      instMac(xA, xB),
      instMacAcc(xA, xB),
      instJalX0,
    )

    val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val cWords = Seq(100, 120, 228, 280)
    val expectedC = packBytesLE(int32sToBytesLE(cWords), beatBytes)

    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var cWrites = 0
      var lastWriteData = BigInt(0)

      for (_ <- 0 until 8000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        val dbusReadAddr = dut.io.dbus.addr.peek().litValue
        dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
        dut.io.dbus.ready.poke(true.B)

        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
          if (dut.io.dbus.write.peek().litToBoolean) {
            val addr = dut.io.dbus.addr.peek().litValue
            val wdata = dut.io.dbus.wdata.peek().litValue
            if (addr == BigInt(cBase)) {
              cWrites += 1
              lastWriteData = wdata
            }
          }
        }

        dut.clock.step()
      }

      assert(cWrites >= 1, "expected at least one C writeback")
      assert(lastWriteData == expectedC, s"MAC+MAC_ACC C wdata: got 0x${lastWriteData.toString(16)}")
    }
  }

  "Case3 FENCE.I after MAC does not dispatch while matrix/DBus path is busy" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000

    val xA = 10
    val xB = 11
    val xC = 12

    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      instMac(xA, xB),
      instFenceI,
      instJalX0,
    )

    val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)

    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var macDispatched = false
      var sawFenceAtSlot0 = false
      var sawCWrite = false
      var fenceFiredBeforeCWrite = false

      for (_ <- 0 until 6000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        val dbusReadAddr = dut.io.dbus.addr.peek().litValue
        dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
        dut.io.dbus.ready.poke(true.B)

        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
          if (dut.io.dbus.write.peek().litToBoolean) {
            val addr = dut.io.dbus.addr.peek().litValue
            if (addr == BigInt(cBase)) {
              sawCWrite = true
            }
          }
        }

        val inst0 = dut.io.debug.dispatch(0).instInst.peek().litValue
        val fire0 = dut.io.debug.dispatch(0).instFire.peek().litToBoolean
        if (inst0 == BigInt(instFenceI & 0xffffffffL)) {
          sawFenceAtSlot0 = true
          if (fire0 && macDispatched && !sawCWrite) {
            fenceFiredBeforeCWrite = true
          }
        }

        if (inst0 == BigInt(instMac(xA, xB) & 0xffffffffL) && fire0) {
          macDispatched = true
        }

        dut.clock.step()
      }

      assert(macDispatched, "MAC should dispatch")
      assert(sawFenceAtSlot0, "FENCE.I should reach slot0 decode")
      assert(sawCWrite, "expected C writeback (matrix finished)")
      assert(!fenceFiredBeforeCWrite, "FENCE.I must not dispatch until matrix completes (lsuActive interlock)")
    }
  }

  /**
    * Two independent SET_C; MAC pairs with disjoint A/B/C regions — verifies cBaseReg / backend
    * do not leak the first tile into the second (queue + cur/next + writeback).
    *
    * Block0: A@0x10000, B@0x11000, C@0x12000 — same 1..8 / 1..8 pattern as Case1 (expected E0).
    * Block1: A@0x13000, B@0x14000, C@0x15000 — all A=2, all B=3 => C tile 24,24,24,24 (E1).
    */
  "Case4 two disjoint SET_C+MAC sequences: correct C per block, no cross-tile state" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val a0 = 0x10000
    val b0 = 0x11000
    val c0 = 0x12000
    val a1 = 0x13000
    val b1 = 0x14000
    val c1 = 0x15000

    val xA = 10
    val xB = 11
    val xC = 12

    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      instMac(xA, xB),
      lui(xA, 0x13),
      lui(xB, 0x14),
      lui(xC, 0x15),
      instSetC(xC),
      instMac(xA, xB),
      instJalX0,
    )

    val a0Bytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val b0Bytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val c0Words = Seq(50, 60, 114, 140)
    val expectedC0 = packBytesLE(int32sToBytesLE(c0Words), beatBytes)

    val a1Bytes = Seq.fill(8)(2)
    val b1Bytes = Seq.fill(8)(3)
    val c1Words = Seq(24, 24, 24, 24)
    val expectedC1 = packBytesLE(int32sToBytesLE(c1Words), beatBytes)

    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- a0Bytes.zipWithIndex) { dmem(BigInt(a0 + i)) = b }
    for ((b, i) <- b0Bytes.zipWithIndex) { dmem(BigInt(b0 + i)) = b }
    for ((b, i) <- a1Bytes.zipWithIndex) { dmem(BigInt(a1 + i)) = b }
    for ((b, i) <- b1Bytes.zipWithIndex) { dmem(BigInt(b1 + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var sawWriteC0 = false
      var sawWriteC1 = false
      var lastWdataC0 = BigInt(0)
      var lastWdataC1 = BigInt(0)
      var sawWrongC1 = false

      for (_ <- 0 until 12000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        val dbusReadAddr = dut.io.dbus.addr.peek().litValue
        dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
        dut.io.dbus.ready.poke(true.B)

        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
          if (dut.io.dbus.write.peek().litToBoolean) {
            val addr = dut.io.dbus.addr.peek().litValue
            val wdata = dut.io.dbus.wdata.peek().litValue
            val wmask = dut.io.dbus.wmask.peek().litValue
            dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
            if (addr == BigInt(c0)) {
              sawWriteC0 = true
              lastWdataC0 = wdata
            }
            if (addr == BigInt(c1)) {
              if (wdata == expectedC0) {
                sawWrongC1 = true
              }
              sawWriteC1 = true
              lastWdataC1 = wdata
            }
          }
        }

        dut.clock.step()
      }

      assert(sawWriteC0, "expected at least one matrix write to cBase0")
      assert(sawWriteC1, "expected at least one matrix write to cBase1")
      assert(lastWdataC0 == expectedC0, s"cBase0 final C beat: got 0x${lastWdataC0.toString(16)} exp 0x${expectedC0.toString(16)}")
      assert(lastWdataC1 == expectedC1, s"cBase1 final C beat: got 0x${lastWdataC1.toString(16)} exp 0x${expectedC1.toString(16)}")
      assert(!sawWrongC1, "C base1 must not see first block's expected tile data (state leak)")
    }
  }

  /**
    * Scalar lw/sw around SET_C+MAC: DBus arbitration (`useMatrix` vs LSU) must not drop or reorder
    * scalar traffic vs matrix traffic.
    */
  "Case5 scalar lw/sw bracketing SET_C+MAC: scratch memory + matrix C both correct" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val scratch = 0x20000
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000

    val xPtr = 5
    val xVal = 6
    val xLd = 7
    val xA = 10
    val xB = 11
    val xC = 12

    val prog = Array(
      lui(xPtr, 0x20),
      instAddi(xVal, 0, 0x42),
      instSw(xVal, xPtr, 0),
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      instMac(xA, xB),
      instLw(xLd, xPtr, 0),
      instJalX0,
    )

    val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val cWords = Seq(50, 60, 114, 140)
    val expectedC = packBytesLE(int32sToBytesLE(cWords), beatBytes)

    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var sawSwScratch = false
      var sawCWrite = false
      var lastCdata = BigInt(0)
      var dbusReadsToScratch = 0

      for (_ <- 0 until 8000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        val dbusReadAddr = dut.io.dbus.addr.peek().litValue
        dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
        dut.io.dbus.ready.poke(true.B)

        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
          val isWrite = dut.io.dbus.write.peek().litToBoolean
          val addr = dut.io.dbus.addr.peek().litValue
          if (!isWrite && addr == BigInt(scratch)) {
            dbusReadsToScratch += 1
          }
          if (isWrite) {
            val wdata = dut.io.dbus.wdata.peek().litValue
            val wmask = dut.io.dbus.wmask.peek().litValue
            dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
            if (addr == BigInt(scratch)) {
              sawSwScratch = true
            }
            if (addr == BigInt(cBase)) {
              sawCWrite = true
              lastCdata = wdata
            }
          }
        }

        dut.clock.step()
      }

      assert(sawSwScratch, "expected scalar SW to scratch (DBus write)")
      val scratchWord = dmemLoad128(dmem, BigInt(scratch), beatBytes) & BigInt("ffffffff", 16)
      assert(
        scratchWord == BigInt(0x42),
        s"scratch after SW+MAC+LW model: low 32b got 0x${scratchWord.toString(16)} exp 0x42",
      )
      assert(dbusReadsToScratch >= 1, "expected at least one DBus read to scratch (LW)")
      assert(sawCWrite, "expected matrix writeback to C")
      assert(lastCdata == expectedC, s"matrix C wdata got 0x${lastCdata.toString(16)}")
    }
  }

  /**
    * SET_C alone (no MAC): matrixComplete / matrixArchInflight must not wedge the core — FENCE.I
    * must still dispatch after SET_C finishes (inflight returns to 0).
    */
  "Case6A SET_C only then FENCE.I: inflight does not stick, fence can dispatch" in {
    val p = mkParams()
    val xC = 12
    val prog = Array(
      lui(xC, 0x12),
      instSetC(xC),
      instFenceI,
      instJalX0,
    )

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var setcFireCycle = Option.empty[Int]
      var fenceFireCycle = Option.empty[Int]
      var cyc = 0

      for (_ <- 0 until 4000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        dut.io.dbus.rdata.poke(0.U(p.lsuDataBits.W))
        dut.io.dbus.ready.poke(true.B)

        val inst0 = dut.io.debug.dispatch(0).instInst.peek().litValue
        val fire0 = dut.io.debug.dispatch(0).instFire.peek().litToBoolean
        if (inst0 == BigInt(instSetC(xC) & 0xffffffffL) && fire0) {
          if (setcFireCycle.isEmpty) {
            setcFireCycle = Some(cyc)
          }
        }
        if (inst0 == BigInt(instFenceI & 0xffffffffL) && fire0) {
          if (fenceFireCycle.isEmpty) {
            fenceFireCycle = Some(cyc)
          }
        }

        dut.clock.step()
        cyc += 1
      }

      assert(setcFireCycle.nonEmpty, "SET_C should dispatch (fire)")
      assert(fenceFireCycle.nonEmpty, "FENCE.I should dispatch — matrixArchInflight must not stay stuck after SET_C")
      assert(
        fenceFireCycle.get > setcFireCycle.get,
        "FENCE.I must fire after SET_C (program order + inflight cleared)",
      )
    }
  }

  /** SET_C + MAC + MAC_ACC ×2: final C tile should be exactly 3× the single-MAC tile (Case1 baseline). */
  "Case6B SET_C + MAC + MAC_ACC + MAC_ACC triple accumulation" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000

    val xA = 10
    val xB = 11
    val xC = 12

    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      instMac(xA, xB),
      instMacAcc(xA, xB),
      instMacAcc(xA, xB),
      instJalX0,
    )

    val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
    val baseWords = Seq(50, 60, 114, 140)
    val cWords = baseWords.map(_ * 3)
    val expectedC = packBytesLE(int32sToBytesLE(cWords), beatBytes)

    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var lastWriteData = BigInt(0)
      var cWrites = 0

      for (_ <- 0 until 12000) {
        val ibAddr = dut.io.ibus.addr.peek().litValue
        dut.io.ibus.fault.valid.poke(false.B)
        dut.io.ebus.fault.valid.poke(false.B)
        if (dut.io.ibus.valid.peek().litToBoolean) {
          dut.io.ibus.ready.poke(true.B)
          dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
        } else {
          dut.io.ibus.ready.poke(false.B)
        }

        val dbusReadAddr = dut.io.dbus.addr.peek().litValue
        dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
        dut.io.dbus.ready.poke(true.B)

        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
          if (dut.io.dbus.write.peek().litToBoolean) {
            val addr = dut.io.dbus.addr.peek().litValue
            if (addr == BigInt(cBase)) {
              cWrites += 1
              lastWriteData = dut.io.dbus.wdata.peek().litValue
            }
          }
        }

        dut.clock.step()
      }

      assert(cWrites >= 1, "expected at least one C writeback")
      assert(lastWriteData == expectedC, s"triple MAC_ACC C: got 0x${lastWriteData.toString(16)} exp 0x${expectedC.toString(16)}")
    }
  }
}
