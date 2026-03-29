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
import org.scalatest.freespec.AnyFreeSpec

class MatrixEngineSpec extends AnyFreeSpec with ChiselSim {
  private def waitDone(dut: MatrixEngine, maxCycles: Int = 64): Unit = {
    var seen = false
    var cycles = 0
    while (!seen && cycles < maxCycles) {
      if (dut.io.done.peek().litToBoolean) {
        seen = true
      } else {
        dut.clock.step()
        cycles += 1
      }
    }
    assert(seen, s"MatrixEngine did not assert done within $maxCycles cycles")
  }

  private def pokeCmd(
      dut: MatrixEngine,
      clear: Boolean,
      aRow: Seq[Seq[Int]],
      bCol: Seq[Seq[Int]],
  ): Unit = {
    dut.io.cmd.valid.poke(true.B)
    dut.io.cmd.bits.clear.poke(clear.B)
    for (i <- aRow.indices) {
      for (k <- aRow(i).indices) {
        dut.io.cmd.bits.aRow(i)(k).poke(aRow(i)(k).S)
      }
    }
    for (j <- bCol.indices) {
      for (k <- bCol(j).indices) {
        dut.io.cmd.bits.bCol(j)(k).poke(bCol(j)(k).S)
      }
    }
  }

  "MatrixEngine clear + vdot computes expected 2x2x4 tile" in {
    simulate(new MatrixEngine(m = 2, n = 2, k = 4)) { dut =>
      // A rows:
      // [1 2 3 4]
      // [5 6 7 8]
      val a = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
      )
      // B columns (k-major source matrix rows are [1 2], [3 4], [5 6], [7 8]):
      // col0 = [1 3 5 7], col1 = [2 4 6 8]
      val b = Seq(
        Seq(1, 3, 5, 7),
        Seq(2, 4, 6, 8),
      )

      pokeCmd(dut, clear = true, a, b)
      dut.io.cmd.ready.expect(true.B)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      waitDone(dut)

      // C = A(2x4) * B(4x2)
      dut.io.acc(0)(0).expect(50.S)  // 1*1 + 2*3 + 3*5 + 4*7
      dut.io.acc(0)(1).expect(60.S)  // 1*2 + 2*4 + 3*6 + 4*8
      dut.io.acc(1)(0).expect(114.S) // 5*1 + 6*3 + 7*5 + 8*7
      dut.io.acc(1)(1).expect(140.S) // 5*2 + 6*4 + 7*6 + 8*8
    }
  }

  "MatrixEngine accumulate mode adds over previous result" in {
    simulate(new MatrixEngine(m = 2, n = 2, k = 4)) { dut =>
      val a = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
      )
      val b = Seq(
        Seq(1, 3, 5, 7),
        Seq(2, 4, 6, 8),
      )

      pokeCmd(dut, clear = true, a, b)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      waitDone(dut)
      dut.clock.step()

      pokeCmd(dut, clear = false, a, b)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      waitDone(dut)

      dut.io.acc(0)(0).expect((50 * 2).S)
      dut.io.acc(0)(1).expect((60 * 2).S)
      dut.io.acc(1)(0).expect((114 * 2).S)
      dut.io.acc(1)(1).expect((140 * 2).S)
    }
  }

  "MatrixEngine done is delayed one cycle after cmd.fire" in {
    simulate(new MatrixEngine(m = 2, n = 2, k = 4)) { dut =>
      val a = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
      )
      val b = Seq(
        Seq(1, 3, 5, 7),
        Seq(2, 4, 6, 8),
      )

      pokeCmd(dut, clear = true, a, b)
      dut.io.done.expect(false.B)
      // Same cycle fire: accumulators have not committed yet.
      dut.io.acc(0)(0).expect(0.S)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // One cycle later done must pulse, and acc must be updated.
      dut.io.done.expect(true.B)
      dut.io.acc(0)(0).expect(50.S)
      dut.io.acc(0)(1).expect(60.S)
      dut.io.acc(1)(0).expect(114.S)
      dut.io.acc(1)(1).expect(140.S)
      dut.clock.step()
      dut.io.done.expect(false.B)
    }
  }

  "MatrixEngine supports configurable K (k=2 smoke)" in {
    simulate(new MatrixEngine(m = 1, n = 1, k = 2)) { dut =>
      val a = Seq(Seq(3, 4))     // 1x2
      val b = Seq(Seq(5, 6))     // 1 col, length 2

      pokeCmd(dut, clear = true, a, b)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      waitDone(dut)

      // 3*5 + 4*6 = 39
      dut.io.acc(0)(0).expect(39.S)
    }
  }
}

