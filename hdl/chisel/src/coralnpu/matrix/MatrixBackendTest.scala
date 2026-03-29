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
import chisel3.simulator.scalatest.ChiselSim
import coralnpu.Parameters
import org.scalatest.freespec.AnyFreeSpec

class MatrixBackendSpec extends AnyFreeSpec with ChiselSim {
  private def packBytesLE(bytes: Seq[Int], widthBytes: Int): BigInt = {
    val padded = bytes ++ Seq.fill(widthBytes - bytes.length)(0)
    padded.zipWithIndex.map { case (b, idx) =>
      (BigInt(b & 0xFF) << (8 * idx))
    }.foldLeft(BigInt(0))(_ | _)
  }

  private def int32sToBytesLE(words: Seq[Int]): Seq[Int] = {
    words.flatMap { w =>
      Seq(
        (w >>> 0) & 0xFF,
        (w >>> 8) & 0xFF,
        (w >>> 16) & 0xFF,
        (w >>> 24) & 0xFF,
      )
    }
  }

  "MatrixBackend loads A/B and writes expected C tile (2x2xK, 128b bus)" in {
    val p = new Parameters
    p.lsuDataBits = 128
    p.matrixM = 2
    p.matrixN = 2
    p.matrixK = 4

    simulate(new MatrixBackend(p)) { dut =>
      val aBase = 0x1000
      val bBase = 0x2000
      val cBase = 0x3000
      val beatBytes = p.lsuDataBits / 8

      // A rows: [1 2 3 4], [5 6 7 8]
      val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
      // B is KxN laid out as rows: [1 2], [3 4], [5 6], [7 8]
      val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)

      // Expected C is 2x2 int32 (row-major):
      // [[50,  60],
      //  [114, 140]]
      val cWords = Seq(50, 60, 114, 140)
      val cBytes = int32sToBytesLE(cWords)
      val expectedWrite = packBytesLE(cBytes, beatBytes)

      dut.io.mem.req.ready.poke(true.B)
      dut.io.mem.resp.valid.poke(false.B)
      dut.io.mem.resp.bits.rdata.poke(0.U)
      dut.io.mem.resp.bits.error.poke(false.B)

      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.bits.op.poke(MatrixOp.MAC)
      dut.io.cmd.bits.pc.poke(0x44.U)
      dut.io.cmd.bits.aBase.poke(aBase.U)
      dut.io.cmd.bits.bBase.poke(bBase.U)
      dut.io.cmd.bits.cBase.poke(cBase.U)

      var cmdSent = false
      var nextRespValid = false
      var nextRespData = BigInt(0)
      var sawWrite = false
      var writeAddr = BigInt(0)
      var writeData = BigInt(0)
      var writeMask = BigInt(0)
      var pendingDropValid = false

      for (_ <- 0 until 80) {
        dut.io.mem.resp.valid.poke(nextRespValid.B)
        dut.io.mem.resp.bits.rdata.poke(nextRespData.U)
        nextRespValid = false
        nextRespData = BigInt(0)

        val cmdWillFire =
          dut.io.cmd.valid.peek().litToBoolean &&
          dut.io.cmd.ready.peek().litToBoolean

        val reqFire =
          dut.io.mem.req.valid.peek().litToBoolean &&
          dut.io.mem.req.ready.peek().litToBoolean

        if (reqFire) {
          val isWrite = dut.io.mem.req.bits.write.peek().litToBoolean
          val addr = dut.io.mem.req.bits.addr.peek().litValue
          if (!isWrite) {
            if (addr == BigInt(aBase)) {
              nextRespValid = true
              nextRespData = packBytesLE(aBytes, beatBytes)
            } else if (addr == BigInt(bBase)) {
              nextRespValid = true
              nextRespData = packBytesLE(bBytes, beatBytes)
            }
          } else {
            sawWrite = true
            writeAddr = addr
            writeData = dut.io.mem.req.bits.wdata.peek().litValue
            writeMask = dut.io.mem.req.bits.wmask.peek().litValue
          }
        }

        dut.clock.step()

        if (cmdWillFire) {
          cmdSent = true
          dut.io.cmd.valid.poke(false.B)
        }
      }

      assert(cmdSent, "MAC command should be accepted by backend")
      assert(sawWrite, "Backend should emit a writeback request")
      assert(writeAddr == BigInt(cBase), s"Unexpected write addr: 0x${writeAddr.toString(16)}")
      assert(writeData == expectedWrite, s"Unexpected write data: 0x${writeData.toString(16)}")
      assert(writeMask == BigInt("ffff", 16), s"Unexpected write mask: 0x${writeMask.toString(16)}")
      dut.io.busy.expect(false.B)
    }
  }

  "MatrixBackend MAC_ACC accumulates over existing tile state" in {
    val p = new Parameters
    p.lsuDataBits = 128
    p.matrixM = 2
    p.matrixN = 2
    p.matrixK = 4

    simulate(new MatrixBackend(p)) { dut =>
      val aBase = 0x1000
      val bBase = 0x2000
      val cBase = 0x3000
      val beatBytes = p.lsuDataBits / 8
      val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
      val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)

      val cWords = Seq(50 * 2, 60 * 2, 114 * 2, 140 * 2)
      val cBytes = int32sToBytesLE(cWords)
      val expectedWrite = packBytesLE(cBytes, beatBytes)

      dut.io.mem.req.ready.poke(true.B)
      dut.io.mem.resp.valid.poke(false.B)
      dut.io.mem.resp.bits.rdata.poke(0.U)
      dut.io.mem.resp.bits.error.poke(false.B)

      var firstSent = false
      var secondSent = false
      var secondQueued = false
      var driveSecondCmd = false

      var nextRespValid = false
      var nextRespData = BigInt(0)

      var writeCount = 0
      var secondWriteData = BigInt(0)

      for (cycle <- 0 until 160) {
        if (!firstSent) {
          dut.io.cmd.valid.poke(true.B)
          dut.io.cmd.bits.op.poke(MatrixOp.MAC)
          dut.io.cmd.bits.pc.poke(0x44.U)
          dut.io.cmd.bits.aBase.poke(aBase.U)
          dut.io.cmd.bits.bBase.poke(bBase.U)
          dut.io.cmd.bits.cBase.poke(cBase.U)
        } else if (driveSecondCmd && !secondSent) {
          dut.io.cmd.valid.poke(true.B)
          dut.io.cmd.bits.op.poke(MatrixOp.MAC_ACC)
          dut.io.cmd.bits.pc.poke(0x48.U)
          dut.io.cmd.bits.aBase.poke(aBase.U)
          dut.io.cmd.bits.bBase.poke(bBase.U)
          dut.io.cmd.bits.cBase.poke(cBase.U)
        } else {
          dut.io.cmd.valid.poke(false.B)
        }

        dut.io.mem.resp.valid.poke(nextRespValid.B)
        dut.io.mem.resp.bits.rdata.poke(nextRespData.U)
        nextRespValid = false
        nextRespData = BigInt(0)

        val cmdWillFire =
          dut.io.cmd.valid.peek().litToBoolean &&
          dut.io.cmd.ready.peek().litToBoolean

        val reqFire =
          dut.io.mem.req.valid.peek().litToBoolean &&
          dut.io.mem.req.ready.peek().litToBoolean

        if (cmdWillFire) {
          val pc = dut.io.cmd.bits.pc.peek().litValue
          println(s"[CMD  ] cycle=$cycle fire pc=0x${pc.toString(16)}")
        }

        if (reqFire) {
          val isWrite = dut.io.mem.req.bits.write.peek().litToBoolean
          val addr = dut.io.mem.req.bits.addr.peek().litValue

          if (isWrite) {
            val data = dut.io.mem.req.bits.wdata.peek().litValue
            val mask = dut.io.mem.req.bits.wmask.peek().litValue
            writeCount += 1
            println(s"[WRITE] cycle=$cycle count=$writeCount addr=0x${addr.toString(16)} mask=0x${mask.toString(16)} data=0x${data.toString(16)}")

            if (writeCount == 1 && !secondQueued) {
              secondQueued = true
              driveSecondCmd = true   
              println(s"[TEST ] cycle=$cycle arm second MAC_ACC for next cycle")
            }

            if (writeCount == 2) {
              secondWriteData = data
            }
          } else {
            println(s"[READ ] cycle=$cycle addr=0x${addr.toString(16)}")
            if (addr == BigInt(aBase)) {
              nextRespValid = true
              nextRespData = packBytesLE(aBytes, beatBytes)
            } else if (addr == BigInt(bBase)) {
              nextRespValid = true
              nextRespData = packBytesLE(bBytes, beatBytes)
            }
          }
        }

        dut.clock.step()

        if (cmdWillFire && !firstSent) {
          firstSent = true
        } else if (cmdWillFire && firstSent && !secondSent) {
          secondSent = true
          driveSecondCmd = false
        }
      }

      println(s"[TEST ] summary firstSent=$firstSent secondSent=$secondSent secondQueued=$secondQueued writeCount=$writeCount")

      assert(firstSent, "First MAC should be accepted")
      assert(secondQueued, "Second MAC_ACC should be queued")
      assert(secondSent, "Second MAC_ACC should be accepted")
      assert(writeCount == 2, s"Expected exactly two writebacks, saw $writeCount")
      assert(secondWriteData == expectedWrite,
        s"Unexpected accumulated write data: 0x${secondWriteData.toString(16)}")
    }
  }
  "MatrixBackend handles variable resp.valid latency" in {
    val p = new Parameters
    p.lsuDataBits = 128
    p.matrixM = 2
    p.matrixN = 2
    p.matrixK = 4

    simulate(new MatrixBackend(p)) { dut =>
      val aBase = 0x1000
      val bBase = 0x2000
      val cBase = 0x3000
      val beatBytes = p.lsuDataBits / 8
      val aBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
      val bBytes = Seq(1, 2, 3, 4, 5, 6, 7, 8)
      val cWords = Seq(50, 60, 114, 140)
      val expectedWrite = packBytesLE(int32sToBytesLE(cWords), beatBytes)

      case class PendingResp(delay: Int, data: BigInt)
      var pending: Option[PendingResp] = None

      dut.io.mem.req.ready.poke(true.B)
      dut.io.mem.resp.valid.poke(false.B)
      dut.io.mem.resp.bits.rdata.poke(0.U)
      dut.io.mem.resp.bits.error.poke(false.B)
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.bits.op.poke(MatrixOp.MAC)
      dut.io.cmd.bits.pc.poke(0x44.U)
      dut.io.cmd.bits.aBase.poke(aBase.U)
      dut.io.cmd.bits.bBase.poke(bBase.U)
      dut.io.cmd.bits.cBase.poke(cBase.U)

      var cmdSent = false
      var sawWrite = false
      var writeData = BigInt(0)

      for (cycle <- 0 until 120) {
        pending match {
          case Some(PendingResp(0, data)) =>
            dut.io.mem.resp.valid.poke(true.B)
            dut.io.mem.resp.bits.rdata.poke(data.U)
            pending = None
          case Some(PendingResp(d, data)) =>
            dut.io.mem.resp.valid.poke(false.B)
            dut.io.mem.resp.bits.rdata.poke(0.U)
            pending = Some(PendingResp(d - 1, data))
          case None =>
            dut.io.mem.resp.valid.poke(false.B)
            dut.io.mem.resp.bits.rdata.poke(0.U)
        }

        val cmdWillFire = dut.io.cmd.valid.peek().litToBoolean && dut.io.cmd.ready.peek().litToBoolean
        val reqFire = dut.io.mem.req.valid.peek().litToBoolean && dut.io.mem.req.ready.peek().litToBoolean
        if (reqFire) {
          val isWrite = dut.io.mem.req.bits.write.peek().litToBoolean
          val addr = dut.io.mem.req.bits.addr.peek().litValue
          if (!isWrite) {
            val delay = (cycle % 4) + 1 // deterministic variable delay 1..4 cycles.
            val data =
              if (addr == BigInt(aBase)) packBytesLE(aBytes, beatBytes)
              else if (addr == BigInt(bBase)) packBytesLE(bBytes, beatBytes)
              else BigInt(0)
            pending = Some(PendingResp(delay, data))
          } else {
            sawWrite = true
            writeData = dut.io.mem.req.bits.wdata.peek().litValue
          }
        }

        dut.clock.step()
        if (cmdWillFire && !cmdSent) {
          cmdSent = true
          dut.io.cmd.valid.poke(false.B)
        }
      }

      assert(cmdSent, "MAC command should be accepted")
      assert(sawWrite, "Backend should emit writeback with delayed responses")
      assert(writeData == expectedWrite, s"Unexpected write data with delayed responses: 0x${writeData.toString(16)}")
    }
  }
}

