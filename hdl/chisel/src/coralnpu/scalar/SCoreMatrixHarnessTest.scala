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

  /**
    * Matrix harness configuration for this spec.
    *
    * Note: `CoralNpuMatrixCoreParameters()` is the 2×2×4 "fast sim" config.
    * This spec targets 8×8×4 tiles (A=8×4, B=4×8, C=8×8) while keeping 128b I/D bus.
    */
  private def mkParams(): Parameters = {
    val p = CoralNpuMatrixCoreParameters()
    p.matrixM = 8
    p.matrixN = 8
    p.matrixK = 4
    p
  }

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

  private def s8(x: Int): Int = {
    val b = x & 0xff
    if ((b & 0x80) != 0) b - 256 else b
  }

  private def refMatMulS8S8Acc32(aBytes: Seq[Int], bBytes: Seq[Int], m: Int, k: Int, n: Int): Seq[Int] = {
    require(aBytes.length == m * k, s"A bytes length ${aBytes.length} != m*k ${m * k}")
    require(bBytes.length == k * n, s"B bytes length ${bBytes.length} != k*n ${k * n}")
    val out = Array.fill(m * n)(0)
    for (i <- 0 until m) {
      for (j <- 0 until n) {
        var acc = 0
        for (kk <- 0 until k) {
          val a = s8(aBytes(i * k + kk))
          val b = s8(bBytes(kk * n + j))
          acc += a * b
        }
        out(i * n + j) = acc
      }
    }
    out.toSeq
  }

  private def dmemReadBytes(dmem: scala.collection.mutable.Map[BigInt, Int], addr: Int, len: Int): Seq[Int] = {
    (0 until len).map(i => dmem(BigInt(addr + i)) & 0xff)
  }

  private def ceilDiv(x: Int, y: Int): Int = {
    require(y > 0)
    (x + y - 1) / y
  }

  private def dmemReadInt32LE(dmem: scala.collection.mutable.Map[BigInt, Int], addr: Int): Int = {
    val b0 = dmem(BigInt(addr + 0)) & 0xff
    val b1 = dmem(BigInt(addr + 1)) & 0xff
    val b2 = dmem(BigInt(addr + 2)) & 0xff
    val b3 = dmem(BigInt(addr + 3)) & 0xff
    (b0 << 0) | (b1 << 8) | (b2 << 16) | (b3 << 24)
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

  // "Case1 SET_C + MAC writes expected C via full core path" in {
  //   val p = mkParams()
  //   val beatBytes = p.lsuDataBits / 8
  //   val aBase = 0x10000
  //   val bBase = 0x11000
  //   val cBase = 0x12000

  //   val xA = 10
  //   val xB = 11
  //   val xC = 12

  //   val prog = Array(
  //     lui(xA, 0x10),
  //     lui(xB, 0x11),
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     instJalX0,
  //   )

  //   val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
  //   val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
  //   val cWords = refMatMulS8S8Acc32(aBytes, bBytes, p.matrixM, p.matrixK, p.matrixN)
  //   val expectedCBytes = int32sToBytesLE(cWords)
  //   val expectedCBeats = ceilDiv(expectedCBytes.length, beatBytes)

  //   val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
  //   for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
  //   for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var sawCWrite = false
  //     val cWriteBeats = scala.collection.mutable.Set[BigInt]()

  //     var done = false
  //     var cyc = 0
  //     while (cyc < 50000 && !done) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       val dbusReadAddr = dut.io.dbus.addr.peek().litValue
  //       dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))

  //       dut.io.dbus.ready.poke(true.B)

  //       if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
  //         if (dut.io.dbus.write.peek().litToBoolean) {
  //           val addr = dut.io.dbus.addr.peek().litValue
  //           val wdata = dut.io.dbus.wdata.peek().litValue
  //           val wmask = dut.io.dbus.wmask.peek().litValue
  //           dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
  //           if (addr >= BigInt(cBase) && addr < BigInt(cBase + expectedCBytes.length)) {
  //             sawCWrite = true
  //             cWriteBeats += addr
  //           }
  //         }
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //       if (cWriteBeats.size >= expectedCBeats) {
  //         done = true
  //       }
  //     }

  //     assert(sawCWrite, "expected matrix writeback to C base")
  //     val gotCBytes = dmemReadBytes(dmem, cBase, expectedCBytes.length)
  //     assert(gotCBytes == expectedCBytes, "C bytes mismatch for 8x4 * 4x8 => 8x8")
  //   }
  // }

  "Perf1_single_mac_latency" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000
    val cBytes = p.matrixM * p.matrixN * 4
    val lastCBeatAddr = cBase + cBytes - beatBytes

    val xA = 10
    val xB = 11
    val xC = 12

    val macInst = instMac(xA, xB)
    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
      macInst,
      instJalX0,
    )

    val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
    val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var macFireCycle = Option.empty[Int]
      var lastCBeatCycle = Option.empty[Int]
      var cyc = 0

      while (cyc < 200000 && lastCBeatCycle.isEmpty) {
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

        // Track MAC dispatch.fire (slot0) cycle.
        val inst0 = dut.io.debug.dispatch(0).instInst.peek().litValue
        val fire0 = dut.io.debug.dispatch(0).instFire.peek().litToBoolean
        if (macFireCycle.isEmpty && fire0 && inst0 == BigInt(macInst & 0xffffffffL)) {
          macFireCycle = Some(cyc)
        }

        // Track last C beat writeback cycle.
        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean && dut.io.dbus.write.peek().litToBoolean) {
          val addr = dut.io.dbus.addr.peek().litValue
          val wdata = dut.io.dbus.wdata.peek().litValue
          val wmask = dut.io.dbus.wmask.peek().litValue
          dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
          if (addr == BigInt(lastCBeatAddr)) {
            lastCBeatCycle = Some(cyc)
          }
        }

        dut.clock.step()
        cyc += 1
      }

      assert(macFireCycle.nonEmpty, "MAC should dispatch (fire) so latency measurement has a start")
      assert(lastCBeatCycle.nonEmpty, "expected to observe last C beat writeback for SET_C+MAC")
      val latency = lastCBeatCycle.get - macFireCycle.get
      assert(latency >= 0, s"latency must be >= 0, got $latency cycles")
      info(s"[Perf1] SET_C+MAC latency (MAC fire -> last C beat) = $latency cycles; m=${p.matrixM} n=${p.matrixN} k=${p.matrixK} beatBytes=$beatBytes")
    }
  }

  "Perf2_steady_state_throughput" in {
    val p = mkParams()
    val beatBytes = p.lsuDataBits / 8
    val aBase = 0x10000
    val bBase = 0x11000
    val cBase = 0x12000
    val cBytes = p.matrixM * p.matrixN * 4
    val lastCBeatAddr = cBase + cBytes - beatBytes

    val xA = 10
    val xB = 11
    val xC = 12

    val nMac = 20
    val macInst = instMac(xA, xB)
    val prog = Array(
      lui(xA, 0x10),
      lui(xB, 0x11),
      lui(xC, 0x12),
      instSetC(xC),
    ) ++ Array.fill(nMac)(macInst) ++ Array(instJalX0)

    val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
    val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
    val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
    for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
    for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

    val resetPc = 0
    val imemBase = 0

    simulate(new SCore(p)) { dut =>
      applyReset(dut)
      tieOffMinimal(dut, p)
      dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

      var firstMacFireCycle = Option.empty[Int]
      var nthCompleteCycle = Option.empty[Int]
      var completed = 0
      var cyc = 0

      while (cyc < 800000 && nthCompleteCycle.isEmpty) {
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

        // Track first MAC dispatch.fire (slot0) cycle.
        val inst0 = dut.io.debug.dispatch(0).instInst.peek().litValue
        val fire0 = dut.io.debug.dispatch(0).instFire.peek().litToBoolean
        if (firstMacFireCycle.isEmpty && fire0 && inst0 == BigInt(macInst & 0xffffffffL)) {
          firstMacFireCycle = Some(cyc)
        }

        // Count per-MAC completion by observing last C beat write handshakes.
        if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean && dut.io.dbus.write.peek().litToBoolean) {
          val addr = dut.io.dbus.addr.peek().litValue
          val wdata = dut.io.dbus.wdata.peek().litValue
          val wmask = dut.io.dbus.wmask.peek().litValue
          dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
          if (addr == BigInt(lastCBeatAddr)) {
            completed += 1
            if (completed == nMac) {
              nthCompleteCycle = Some(cyc)
            }
          }
        }

        dut.clock.step()
        cyc += 1
      }

      assert(firstMacFireCycle.nonEmpty, "expected to see first MAC dispatch.fire")
      assert(nthCompleteCycle.nonEmpty, s"expected to see $nMac MAC completions (last C beat writes)")
      val totalCycles = nthCompleteCycle.get - firstMacFireCycle.get
      assert(totalCycles > 0, s"totalCycles must be > 0, got $totalCycles")
      val macsPerInst = p.matrixM * p.matrixN * p.matrixK
      val macsTotal = nMac.toDouble * macsPerInst.toDouble
      val throughput = macsTotal / totalCycles.toDouble
      val cyclesPerMacInst = totalCycles.toDouble / nMac.toDouble
      info(f"[Perf2] steady-state: nMac=$nMac totalCycles=$totalCycles cyclesPerMACinst=$cyclesPerMacInst%.3f MAC/cycle=$throughput%.6f (m=${p.matrixM} n=${p.matrixN} k=${p.matrixK})")
    }
  }

  // "Case2 SET_C + MAC + MAC_ACC accumulates (program-level)" in {
  //   val p = mkParams()
  //   val beatBytes = p.lsuDataBits / 8
  //   val aBase = 0x10000
  //   val bBase = 0x11000
  //   val cBase = 0x12000

  //   val xA = 10
  //   val xB = 11
  //   val xC = 12

  //   val prog = Array(
  //     lui(xA, 0x10),
  //     lui(xB, 0x11),
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     instMacAcc(xA, xB),
  //     instJalX0,
  //   )

  //   val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
  //   val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
  //   val baseWords = refMatMulS8S8Acc32(aBytes, bBytes, p.matrixM, p.matrixK, p.matrixN)
  //   val cWords = baseWords.map(_ * 2)
  //   val expectedCBytes = int32sToBytesLE(cWords)
  //   val expectedCBeats = ceilDiv(expectedCBytes.length, beatBytes)

  //   val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
  //   for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
  //   for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var cWrites = 0
  //     val currRoundBeats = scala.collection.mutable.Set[BigInt]()
  //     var completedRounds = 0

  //     var done = false
  //     var cyc = 0
  //     while (cyc < 80000 && !done) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       val dbusReadAddr = dut.io.dbus.addr.peek().litValue
  //       dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
  //       dut.io.dbus.ready.poke(true.B)

  //       if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
  //         if (dut.io.dbus.write.peek().litToBoolean) {
  //           val addr = dut.io.dbus.addr.peek().litValue
  //           val wdata = dut.io.dbus.wdata.peek().litValue
  //           val wmask = dut.io.dbus.wmask.peek().litValue
  //           dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
  //           if (addr >= BigInt(cBase) && addr < BigInt(cBase + expectedCBytes.length)) {
  //             cWrites += 1
  //             currRoundBeats += addr
  //             if (currRoundBeats.size >= expectedCBeats) {
  //               completedRounds += 1
  //               currRoundBeats.clear()
  //             }
  //           }
  //         }
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //       // MAC + MAC_ACC => 需要两轮完整写回
  //       if (completedRounds >= 2) {
  //         done = true
  //       }
  //     }

  //     assert(cWrites >= 1, "expected at least one C writeback")
  //     assert(
  //       cWrites >= expectedCBeats * 2,
  //       s"expected at least ${expectedCBeats * 2} C write beats (MAC + MAC_ACC), got $cWrites",
  //     )
  //     assert(completedRounds >= 2, "expected two complete C writeback rounds (MAC + MAC_ACC)")
  //     val gotCBytes = dmemReadBytes(dmem, cBase, expectedCBytes.length)
  //     assert(gotCBytes == expectedCBytes, "MAC+MAC_ACC bytes mismatch for 8x8 output")
  //   }
  // }

  // "Case3 FENCE.I after MAC does not dispatch while matrix/DBus path is busy" in {
  //   val p = mkParams()
  //   val beatBytes = p.lsuDataBits / 8
  //   val aBase = 0x10000
  //   val bBase = 0x11000
  //   val cBase = 0x12000
  //   val cBytes = p.matrixM * p.matrixN * 4
  //   val lastCBeatAddr = cBase + cBytes - beatBytes

  //   val xA = 10
  //   val xB = 11
  //   val xC = 12

  //   val prog = Array(
  //     lui(xA, 0x10),
  //     lui(xB, 0x11),
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     instFenceI,
  //     instJalX0,
  //   )

  //   val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
  //   val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)

  //   val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
  //   for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
  //   for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var macDispatched = false
  //     var sawFenceAtSlot0 = false
  //     var sawAnyCWrite = false
  //     var sawLastCBeatWrite = false
  //     var fenceFiredBeforeLastCBeat = false
  //     var cyc = 0

  //     for (_ <- 0 until 80000) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       val dbusReadAddr = dut.io.dbus.addr.peek().litValue
  //       dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
  //       dut.io.dbus.ready.poke(true.B)

  //       if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
  //         if (dut.io.dbus.write.peek().litToBoolean) {
  //           val addr = dut.io.dbus.addr.peek().litValue
  //           if (addr >= BigInt(cBase) && addr < BigInt(cBase + cBytes)) {
  //             sawAnyCWrite = true
  //           }
  //           if (addr == BigInt(lastCBeatAddr)) {
  //             sawLastCBeatWrite = true
  //           }
  //         }
  //       }

  //       val inst0 = dut.io.debug.dispatch(0).instInst.peek().litValue
  //       val fire0 = dut.io.debug.dispatch(0).instFire.peek().litToBoolean
  //       if (inst0 == BigInt(instFenceI & 0xffffffffL)) {
  //         sawFenceAtSlot0 = true
  //         if (fire0 && macDispatched && !sawLastCBeatWrite) {
  //           fenceFiredBeforeLastCBeat = true
  //         }
  //       }

  //       if (inst0 == BigInt(instMac(xA, xB) & 0xffffffffL) && fire0) {
  //         macDispatched = true
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //     }

  //     assert(macDispatched, "MAC should dispatch")
  //     assert(sawFenceAtSlot0, "FENCE.I should reach slot0 decode")
  //     assert(sawAnyCWrite, "expected some C writeback beat")
  //     assert(sawLastCBeatWrite, "expected last C beat writeback (matrix writeback complete)")
  //     assert(!fenceFiredBeforeLastCBeat, "FENCE.I must not dispatch until matrix writeback completes (last C beat)")
  //   }
  // }

  // /**
  //   * Two independent SET_C; MAC pairs with disjoint A/B/C regions — verifies cBaseReg / backend
  //   * do not leak the first tile into the second (queue + cur/next + writeback).
  //   *
  //   * Block0: A@0x10000, B@0x11000, C@0x12000 — same 1..8 / 1..8 pattern as Case1 (expected E0).
  //   * Block1: A@0x13000, B@0x14000, C@0x15000 — all A=2, all B=3 => C tile 24,24,24,24 (E1).
  //   */
  // "Case4 two disjoint SET_C+MAC sequences: correct C per block, no cross-tile state" in {
  //   val p = mkParams()
  //   val beatBytes = p.lsuDataBits / 8
  //   val a0 = 0x10000
  //   val b0 = 0x11000
  //   val c0 = 0x12000
  //   val a1 = 0x13000
  //   val b1 = 0x14000
  //   val c1 = 0x15000

  //   val xA = 10
  //   val xB = 11
  //   val xC = 12

  //   val prog = Array(
  //     lui(xA, 0x10),
  //     lui(xB, 0x11),
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     lui(xA, 0x13),
  //     lui(xB, 0x14),
  //     lui(xC, 0x15),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     instJalX0,
  //   )

  //   val a0Bytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
  //   val b0Bytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
  //   val c0Words = refMatMulS8S8Acc32(a0Bytes, b0Bytes, p.matrixM, p.matrixK, p.matrixN)
  //   val expectedC0Bytes = int32sToBytesLE(c0Words)
  //   val expectedC0Beats = ceilDiv(expectedC0Bytes.length, beatBytes)

  //   val a1Bytes = Seq.fill(p.matrixM * p.matrixK)(2)
  //   val b1Bytes = Seq.fill(p.matrixK * p.matrixN)(3)
  //   val c1Words = refMatMulS8S8Acc32(a1Bytes, b1Bytes, p.matrixM, p.matrixK, p.matrixN)
  //   val expectedC1Bytes = int32sToBytesLE(c1Words)
  //   val expectedC1Beats = ceilDiv(expectedC1Bytes.length, beatBytes)

  //   val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
  //   for ((b, i) <- a0Bytes.zipWithIndex) { dmem(BigInt(a0 + i)) = b }
  //   for ((b, i) <- b0Bytes.zipWithIndex) { dmem(BigInt(b0 + i)) = b }
  //   for ((b, i) <- a1Bytes.zipWithIndex) { dmem(BigInt(a1 + i)) = b }
  //   for ((b, i) <- b1Bytes.zipWithIndex) { dmem(BigInt(b1 + i)) = b }

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var sawWriteC0 = false
  //     var sawWriteC1 = false
  //     val c0WriteBeats = scala.collection.mutable.Set[BigInt]()
  //     val c1WriteBeats = scala.collection.mutable.Set[BigInt]()

  //     var done = false
  //     var cyc = 0
  //     while (cyc < 160000 && !done) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       val dbusReadAddr = dut.io.dbus.addr.peek().litValue
  //       dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
  //       dut.io.dbus.ready.poke(true.B)

  //       if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
  //         if (dut.io.dbus.write.peek().litToBoolean) {
  //           val addr = dut.io.dbus.addr.peek().litValue
  //           val wdata = dut.io.dbus.wdata.peek().litValue
  //           val wmask = dut.io.dbus.wmask.peek().litValue
  //           dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
  //           if (addr >= BigInt(c0) && addr < BigInt(c0 + expectedC0Bytes.length)) {
  //             sawWriteC0 = true
  //             c0WriteBeats += addr
  //           }
  //           if (addr >= BigInt(c1) && addr < BigInt(c1 + expectedC1Bytes.length)) {
  //             sawWriteC1 = true
  //             c1WriteBeats += addr
  //           }
  //         }
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //       if (c0WriteBeats.size >= expectedC0Beats && c1WriteBeats.size >= expectedC1Beats) {
  //         done = true
  //       }
  //     }

  //     assert(sawWriteC0, "expected at least one matrix write to cBase0")
  //     assert(sawWriteC1, "expected at least one matrix write to cBase1")
  //     val gotC0Bytes = dmemReadBytes(dmem, c0, expectedC0Bytes.length)
  //     val gotC1Bytes = dmemReadBytes(dmem, c1, expectedC1Bytes.length)
  //     assert(gotC0Bytes == expectedC0Bytes, "cBase0 C bytes mismatch")
  //     assert(gotC1Bytes == expectedC1Bytes, "cBase1 C bytes mismatch")
  //     assert(gotC1Bytes != expectedC0Bytes, "cBase1 must not equal block0 tile (state leak)")
  //   }
  // }

  // /**
  //   * Scalar lw/sw around SET_C+MAC: DBus arbitration (`useMatrix` vs LSU) must not drop or reorder
  //   * scalar traffic vs matrix traffic.
  //   */
  // "Case5 scalar lw/sw bracketing SET_C+MAC: scratch memory + matrix C both correct" in {
  //   val p = mkParams()
  //   val beatBytes = p.lsuDataBits / 8
  //   val scratch = 0x20000
  //   val aBase = 0x10000
  //   val bBase = 0x11000
  //   val cBase = 0x12000

  //   val xPtr = 5
  //   val xVal = 6
  //   val xLd = 7
  //   val xA = 10
  //   val xB = 11
  //   val xC = 12

  //   val prog = Array(
  //     lui(xPtr, 0x20),
  //     instAddi(xVal, 0, 0x42),
  //     instSw(xVal, xPtr, 0),
  //     lui(xA, 0x10),
  //     lui(xB, 0x11),
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     instLw(xLd, xPtr, 0),
  //     instJalX0,
  //   )

  //   val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
  //   val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
  //   val cWords = refMatMulS8S8Acc32(aBytes, bBytes, p.matrixM, p.matrixK, p.matrixN)
  //   val expectedCBytes = int32sToBytesLE(cWords)
  //   val expectedCBeats = ceilDiv(expectedCBytes.length, beatBytes)

  //   val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
  //   for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
  //   for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var sawSwScratch = false
  //     var sawCWrite = false
  //     var dbusReadsToScratch = 0
  //     val cWriteBeats = scala.collection.mutable.Set[BigInt]()

  //     var done = false
  //     var cyc = 0
  //     while (cyc < 120000 && !done) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       val dbusReadAddr = dut.io.dbus.addr.peek().litValue
  //       dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
  //       dut.io.dbus.ready.poke(true.B)

  //       if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
  //         val isWrite = dut.io.dbus.write.peek().litToBoolean
  //         val addr = dut.io.dbus.addr.peek().litValue
  //         if (!isWrite && addr == BigInt(scratch)) {
  //           dbusReadsToScratch += 1
  //         }
  //         if (isWrite) {
  //           val wdata = dut.io.dbus.wdata.peek().litValue
  //           val wmask = dut.io.dbus.wmask.peek().litValue
  //           dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
  //           if (addr == BigInt(scratch)) {
  //             sawSwScratch = true
  //           }
  //           if (addr >= BigInt(cBase) && addr < BigInt(cBase + expectedCBytes.length)) {
  //             sawCWrite = true
  //             cWriteBeats += addr
  //           }
  //         }
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //       if (cWriteBeats.size >= expectedCBeats && sawSwScratch && dbusReadsToScratch >= 1) {
  //         done = true
  //       }
  //     }

  //     assert(sawSwScratch, "expected scalar SW to scratch (DBus write)")
  //     val scratchWord = BigInt(dmemReadInt32LE(dmem, scratch)) & BigInt("ffffffff", 16)
  //     assert(
  //       scratchWord == BigInt(0x42),
  //       s"scratch after SW+MAC+LW model: low 32b got 0x${scratchWord.toString(16)} exp 0x42",
  //     )
  //     assert(dbusReadsToScratch >= 1, "expected at least one DBus read to scratch (LW)")
  //     assert(sawCWrite, "expected matrix writeback to C")
  //     val gotCBytes = dmemReadBytes(dmem, cBase, expectedCBytes.length)
  //     assert(gotCBytes == expectedCBytes, "matrix C bytes mismatch")
  //   }
  // }

  // /**
  //   * SET_C alone (no MAC): matrixComplete / matrixArchInflight must not wedge the core — FENCE.I
  //   * must still dispatch after SET_C finishes (inflight returns to 0).
  //   */
  // "Case6A SET_C only then FENCE.I: inflight does not stick, fence can dispatch" in {
  //   val p = mkParams()
  //   val xC = 12
  //   val prog = Array(
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instFenceI,
  //     instJalX0,
  //   )

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var setcFireCycle = Option.empty[Int]
  //     var fenceFireCycle = Option.empty[Int]
  //     var cyc = 0

  //     for (_ <- 0 until 4000) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       dut.io.dbus.rdata.poke(0.U(p.lsuDataBits.W))
  //       dut.io.dbus.ready.poke(true.B)

  //       val inst0 = dut.io.debug.dispatch(0).instInst.peek().litValue
  //       val fire0 = dut.io.debug.dispatch(0).instFire.peek().litToBoolean
  //       if (inst0 == BigInt(instSetC(xC) & 0xffffffffL) && fire0) {
  //         if (setcFireCycle.isEmpty) {
  //           setcFireCycle = Some(cyc)
  //         }
  //       }
  //       if (inst0 == BigInt(instFenceI & 0xffffffffL) && fire0) {
  //         if (fenceFireCycle.isEmpty) {
  //           fenceFireCycle = Some(cyc)
  //         }
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //     }

  //     assert(setcFireCycle.nonEmpty, "SET_C should dispatch (fire)")
  //     assert(fenceFireCycle.nonEmpty, "FENCE.I should dispatch — matrixArchInflight must not stay stuck after SET_C")
  //     assert(
  //       fenceFireCycle.get > setcFireCycle.get,
  //       "FENCE.I must fire after SET_C (program order + inflight cleared)",
  //     )
  //   }
  // }

  // /** SET_C + MAC + MAC_ACC ×2: final C tile should be exactly 3× the single-MAC tile (Case1 baseline). */
  // "Case6B SET_C + MAC + MAC_ACC + MAC_ACC triple accumulation" in {
  //   val p = mkParams()
  //   val beatBytes = p.lsuDataBits / 8
  //   val aBase = 0x10000
  //   val bBase = 0x11000
  //   val cBase = 0x12000

  //   val xA = 10
  //   val xB = 11
  //   val xC = 12

  //   val prog = Array(
  //     lui(xA, 0x10),
  //     lui(xB, 0x11),
  //     lui(xC, 0x12),
  //     instSetC(xC),
  //     instMac(xA, xB),
  //     instMacAcc(xA, xB),
  //     instMacAcc(xA, xB),
  //     instJalX0,
  //   )

  //   val aBytes = (0 until (p.matrixM * p.matrixK)).map(i => (i + 1) & 0xff)
  //   val bBytes = (0 until (p.matrixK * p.matrixN)).map(i => (i + 1) & 0xff)
  //   val baseWords = refMatMulS8S8Acc32(aBytes, bBytes, p.matrixM, p.matrixK, p.matrixN)
  //   val cWords = baseWords.map(_ * 3)
  //   val expectedCBytes = int32sToBytesLE(cWords)
  //   val expectedCBeats = ceilDiv(expectedCBytes.length, beatBytes)

  //   val dmem = scala.collection.mutable.Map[BigInt, Int]().withDefaultValue(0)
  //   for ((b, i) <- aBytes.zipWithIndex) { dmem(BigInt(aBase + i)) = b }
  //   for ((b, i) <- bBytes.zipWithIndex) { dmem(BigInt(bBase + i)) = b }

  //   val resetPc = 0
  //   val imemBase = 0

  //   simulate(new SCore(p)) { dut =>
  //     applyReset(dut)
  //     tieOffMinimal(dut, p)
  //     dut.io.csr.in.value(0).poke((resetPc & 0xffffffffL).U)

  //     var cWrites = 0
  //     val currRoundBeats = scala.collection.mutable.Set[BigInt]()
  //     var completedRounds = 0

  //     var done = false
  //     var cyc = 0
  //     while (cyc < 200000 && !done) {
  //       val ibAddr = dut.io.ibus.addr.peek().litValue
  //       dut.io.ibus.fault.valid.poke(false.B)
  //       dut.io.ebus.fault.valid.poke(false.B)
  //       if (dut.io.ibus.valid.peek().litToBoolean) {
  //         dut.io.ibus.ready.poke(true.B)
  //         dut.io.ibus.rdata.poke(imemLine(prog, imemBase, ibAddr.toInt).U(p.fetchDataBits.W))
  //       } else {
  //         dut.io.ibus.ready.poke(false.B)
  //       }

  //       val dbusReadAddr = dut.io.dbus.addr.peek().litValue
  //       dut.io.dbus.rdata.poke(dmemLoad128(dmem, dbusReadAddr, beatBytes).U(p.lsuDataBits.W))
  //       dut.io.dbus.ready.poke(true.B)

  //       if (dut.io.dbus.valid.peek().litToBoolean && dut.io.dbus.ready.peek().litToBoolean) {
  //         if (dut.io.dbus.write.peek().litToBoolean) {
  //           val addr = dut.io.dbus.addr.peek().litValue
  //           val wdata = dut.io.dbus.wdata.peek().litValue
  //           val wmask = dut.io.dbus.wmask.peek().litValue
  //           dmemApplyWrite(dmem, addr, wdata, wmask, beatBytes)
  //           if (addr >= BigInt(cBase) && addr < BigInt(cBase + expectedCBytes.length)) {
  //             cWrites += 1
  //             currRoundBeats += addr
  //             if (currRoundBeats.size >= expectedCBeats) {
  //               completedRounds += 1
  //               currRoundBeats.clear()
  //             }
  //           }
  //         }
  //       }

  //       dut.clock.step()
  //       cyc += 1
  //       // MAC + MAC_ACC + MAC_ACC => 需要三轮完整写回
  //       if (completedRounds >= 3) {
  //         done = true
  //       }
  //     }

  //     assert(cWrites >= 1, "expected at least one C writeback")
  //     assert(
  //       cWrites >= expectedCBeats * 3,
  //       s"expected at least ${expectedCBeats * 3} C write beats (MAC + MAC_ACC + MAC_ACC), got $cWrites",
  //     )
  //     assert(completedRounds >= 3, "expected three complete C writeback rounds (MAC + MAC_ACC + MAC_ACC)")
  //     val gotCBytes = dmemReadBytes(dmem, cBase, expectedCBytes.length)
  //     assert(gotCBytes == expectedCBytes, "triple MAC_ACC bytes mismatch for 8x8 output")
  //   }
  // }
}
