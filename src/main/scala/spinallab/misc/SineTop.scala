package spinallab.misc

import spinal.core._

class SineTop extends Component {
  val clock = in Bool()
  val reset_N = in Bool()
  val triggerOut = out Bool()
  val xOut = out Bool()
  val yOut = out Bool()

  val logicClockDomain = ClockDomain(
    clock = clock,
    reset = reset_N,
    config = ClockDomainConfig(
      resetActiveLevel = LOW
    )
  )

  val logic = new ClockingArea(logicClockDomain) {
    val signalBitNb = 16
    val phaseBitNb = 17
    val tableAddressBitNb = 3
    val oversamplingBitNb = phaseBitNb - 2 - tableAddressBitNb


    val sinX, sinY = new Area {
      val wave = new SinSmooth(
        phaseBitNb = phaseBitNb,
        signalBitNb = signalBitNb,
        tableAddressBitNb = tableAddressBitNb,
        oversamplingBitNb = oversamplingBitNb
      )

      val dac = new SigmaDeltaSecondOrder(parallelWidth = signalBitNb)
      dac.io.parallelIn <> wave.io.sine
    }

    sinX.wave.io.step := 2
    sinY.wave.io.step := 3
    xOut := sinX.dac.io.serialOut
    yOut := sinY.dac.io.serialOut
    triggerOut := sinY.wave.io.square.msb
  }
}





object SineTop {
  import spinal.core.sim._

  def main(args: Array[String]): Unit = {
    SimConfig.doSim(new SineTop) { dut =>
      var idx = 0
      dut.reset_N #= false
      sleep(10)
      dut.reset_N #= true
      sleep(10)
      while (idx < 100000) {
        dut.clock #= false
        sleep(10)
        dut.clock #= true
        sleep(10)
        idx += 1
      }
    }
  }
}