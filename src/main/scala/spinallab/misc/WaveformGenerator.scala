package spinallab.misc

import spinal.core._

class WaveformGenerator(phaseBitNb : Int,
                        signalBitNb : Int,
                        shiftBitNb : Int) extends Component {
  val io = new Bundle {
    val en        = in  Bool
    val step      = in  UInt(phaseBitNb bits)

    val sawtooth  = out UInt(signalBitNb bits)
    val square    = out UInt(signalBitNb bits)
    val triangle  = out UInt(signalBitNb bits)
    val polygon   = out UInt(signalBitNb bits)
    val sine      = out UInt(signalBitNb bits)
  }

  val phase = Reg(UInt(phaseBitNb bits)) init(0)
  when(io.en){
    phase := phase + io.step
  }

  io.sawtooth := phase >> (phaseBitNb - signalBitNb)
  io.square := U(io.square.range -> io.sawtooth.msb)

  io.triangle := io.sawtooth.msb.mux(
    False ->  io.sawtooth,
    True  -> ~io.sawtooth
  ) |<< 1

  val polygon = new Area{
    val mul = io.triangle.resize(widthOf(io.triangle) + 1) + (io.triangle >> 1)
    val anEighth = 1 << (signalBitNb-2)
    val sat = Select(
      (mul < anEighth) -> U(anEighth),
      (mul > 5*anEighth-1) -> U(5*anEighth-1),
      default -> mul
    )
    io.polygon := (sat - anEighth).resized
  }

  val lowpassAcc = Reg(UInt(signalBitNb + shiftBitNb bits)) init(0)
  lowpassAcc := lowpassAcc + io.polygon - (lowpassAcc >> shiftBitNb)
  io.sine := lowpassAcc >> shiftBitNb
}

object WaveformGenerator {
  def main(args: Array[String]) {
    SpinalVhdl(new WaveformGenerator(
      phaseBitNb = 16,
      signalBitNb = 12,
      shiftBitNb = 8
    ))

  }
}

