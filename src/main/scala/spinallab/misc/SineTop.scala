package spinallab.misc

import org.jfree.chart.{ChartFactory, ChartFrame}
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}
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

      //GUI thread
      fork {
        val xSeries = new XYSeries("input")
        val ySeries = new XYSeries("output (filtred)")
        val dataset = new XYSeriesCollection
        dataset.addSeries(xSeries)
        dataset.addSeries(ySeries)
        val chart = ChartFactory.createXYLineChart("input vs output", "time", "value", dataset,PlotOrientation.VERTICAL,true,true,false)
        chart.getXYPlot.getRangeAxis.setAutoRange(false)
        chart.getXYPlot.getRangeAxis.setRange(0, 1)
        val frame = new ChartFrame("Results", chart)
        frame.pack()
        frame.setVisible(true)

        var xFilterState, yFilterState = 0.5
        val coef = 0.002
        val overSampling = 10
        var overSamplingCounter = 0
        while (true) {
          dut.logicClockDomain.waitSampling()
          val xTarget = if (dut.xOut.toBoolean) 1.0 else 0.0
          val yTarget = if (dut.yOut.toBoolean) 1.0 else 0.0
          xFilterState = xFilterState * (1.0 - coef) + xTarget * coef
          yFilterState = yFilterState * (1.0 - coef) + yTarget * coef
          overSamplingCounter += 1
          if (overSamplingCounter == overSampling) {
            overSamplingCounter = 0
            val time = simTime()
            xSeries.add(time, xFilterState)
            ySeries.add(time, yFilterState)
          }
        }
      }

      dut.reset_N #= false
      sleep(10)
      dut.reset_N #= true
      sleep(10)
      for(idx <- 0 to 200000) {
        dut.clock #= false
        sleep(10)
        dut.clock #= true
        sleep(10)
      }
    }
  }
}