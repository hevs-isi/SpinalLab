package spinallab.misc

import spinal.core._

import scala.util.Random

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
  io.square := (default -> io.sawtooth.msb)

  io.triangle := (io.sawtooth ^ U(io.sawtooth.range -> io.sawtooth.msb)) |<< 1

  val polygon = new Area{
    val mul = io.triangle.resize(widthOf(io.triangle) + 1) + (io.triangle >> 1)
    val anEighth = 1 << (signalBitNb-2)
    val sat = Select(
      (mul < anEighth)     -> U(anEighth),
      (mul > 5*anEighth-1) -> U(5*anEighth-1),
      default              -> mul
    )
    io.polygon := (sat - anEighth).resized
  }

  val lowpassAcc = Reg(UInt(signalBitNb + shiftBitNb bits)) init(0)
  lowpassAcc := lowpassAcc + io.polygon - (lowpassAcc >> shiftBitNb)
  io.sine := lowpassAcc >> shiftBitNb
}

object WaveformGeneratorVhdl extends App {
  SpinalVhdl(new WaveformGenerator(
    phaseBitNb = 16,
    signalBitNb = 12,
    shiftBitNb = 8
  ))
}


object WaveformGeneratorSim extends App{
  import spinal.core.sim._

  val simulator = SimConfig.withWave.compile(new WaveformGenerator(
    phaseBitNb = 16,
    signalBitNb = 12,
    shiftBitNb = 8
  ))

  simulator.doSim{dut =>
    dut.clockDomain.forkStimulus(period = 10)
    for(i <- 0 until 50) {
      dut.io.en.randomize()
      dut.io.step #= Random.nextInt(100) + 40
      dut.clockDomain.waitSampling(Random.nextInt(5000))
    }
  }
}