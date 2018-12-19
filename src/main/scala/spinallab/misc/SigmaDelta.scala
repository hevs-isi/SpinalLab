package spinallab.misc

import javax.swing

import spinal.core._
import spinal.sim.Suspendable

import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.xy.XYDataset
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.jfree.data.xy.XYSeriesCollection
import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.chart.ChartFrame

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


object SigmaDelta {
  def main(args: Array[String]): Unit = {
    SpinalVhdl{
      val toplevel = new SigmaDeltaSecondOrder(8)
      toplevel.rework{
        in(UInt(10 bits)).setName("io_debug_filtred")
      }
      toplevel
    }
  }
}

object SigmaDeltaTestbench extends App{
  import spinal.core.sim._
  SimConfig.compile(new SigmaDelta(16)).doSim{ dut =>

    //GUI thread
    fork {
      val input = new XYSeries("input")
      val output = new XYSeries("output (filtred)")
      val dataset = new XYSeriesCollection
      dataset.addSeries(input)
      dataset.addSeries(output)
      val chart = ChartFactory.createXYLineChart("input vs output", "time", "value", dataset,PlotOrientation.VERTICAL,true,true,false)
      chart.getXYPlot.getRangeAxis.setAutoRange(false)
      chart.getXYPlot.getRangeAxis.setRange(0, 1)
      val frame = new ChartFrame("Results", chart)
      frame.pack()
      frame.setVisible(true)

      var hit = 0.5
      val coef = 0.002
      val overSampling = 10
      var overSamplingCounter = 0
      while (true) {
        dut.clockDomain.waitSampling()
        val target = if (dut.io.serialOut.toBoolean) 1.0 else 0.0
        hit = hit * (1.0 - coef) + target * coef
        overSamplingCounter += 1
        if (overSamplingCounter == overSampling) {
          overSamplingCounter = 0
          val time = simTime()
          val value = hit
          input.add(time, dut.io.parallelIn.toInt / 1024.0/64.0)
          output.add(time, value)
        }
      }
    }


    //Stimulus
    dut.clockDomain.forkStimulus(10)
    Suspendable.repeat(5){
      dut.io.parallelIn #= 0x1000
      dut.clockDomain.waitSampling(5000)
      dut.io.parallelIn #= 0xF000
      dut.clockDomain.waitSampling(5000)
    }
  }
}