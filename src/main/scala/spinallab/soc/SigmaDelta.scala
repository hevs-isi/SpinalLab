package spinallab.soc

import spinal.core._


case class DeltaSigmaIo(parallelWidth : Int) extends Bundle{
  val parallelIn = in UInt(parallelWidth bits)
  val serialOut = out Bool
}

class SigmaDelta(parallelWidth : Int) extends Component{
  val io = DeltaSigmaIo(parallelWidth)

  val parallelInAjusted = (io.parallelIn |>> 1) + (1 << parallelWidth-2)

  val accumulator = Reg(UInt(parallelWidth + 1 bits)) init(0)
  accumulator  := accumulator + parallelInAjusted - (io.serialOut ? U(1 << accumulator.high) | U(0))
  io.serialOut := accumulator.msb
}
