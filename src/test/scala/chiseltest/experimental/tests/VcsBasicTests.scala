package chisel3.experimental.tests

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.VcsBackendAnnotation
import org.scalatest._

class VcsBasicTests extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Testers2 with Vcs"

  val annos = Seq(VcsBackendAnnotation)

  it should "build and simulate a basic test with input and output" in {
    assume(firrtl.FileUtils.isVCSAvailable)

    test(new Module {
      val io = IO(new Bundle {
        val in = Input(UInt(8.W))
        val out = Output(UInt(8.W))
      })
      io.out := RegNext(io.in, 0.U)
    }).withAnnotations(annos) { c =>
      c.io.in.poke(0.U)
      c.clock.step()
      c.io.out.expect(0.U)
      c.io.in.poke(42.U)
      c.clock.step()
      c.io.out.expect(42.U)
    }
  }
}
