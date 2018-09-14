package spinallab.misc

import spinal.core._

class SigmaDeltaSecondOrder(parallelWidth : Int) extends Component{
  val io = DeltaSigmaIo(parallelWidth)

  val parallelInAjusted = (io.parallelIn.asSInt ^ S(-1 << io.parallelIn.high)) >> 1
  val accumulatorA = Reg(SInt(parallelWidth + 4 bits)) init(0)
  val accumulatorB = Reg(SInt(parallelWidth + 5 bits)) init(0)
  val c1 = io.serialOut ? S(1 << parallelWidth-1) | S(-1 << parallelWidth-1)
  val c2 = io.serialOut ? S(1 << parallelWidth+3) | S(-1 << parallelWidth+3)
  accumulatorA :=  accumulatorA + parallelInAjusted - c1
  accumulatorB :=  accumulatorB + accumulatorA - c2
  io.serialOut := !accumulatorB.msb
}


object SigmaDeltaSecondOrder {
  def main(args: Array[String]): Unit = {
    SpinalVhdl{
      val toplevel = new SigmaDeltaSecondOrder(8).setDefinitionName("SigmaDelta")
      toplevel.rework{
        in(UInt(10 bits)).setName("io_debug_filtred")
      }
      toplevel
    }
  }
}