package spinallab.soc

import java.awt
import java.awt.event.{ActionEvent, ActionListener, MouseEvent, MouseListener}
import java.io.File

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import vexriscv.demo.{Murax, MuraxConfig}
import javax.swing._

import spinal.lib.com.jtag.sim.JtagTcp
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}

import scala.collection.mutable
import vexriscv.test._

//Openocd => src/openocd -f tcl/interface/jtag_tcp.cfg -c 'set MURAX_CPU0_YAML ../VexRiscvLab/cpu0.yaml' -f tcl/target/murax.cfg
object SocSim {
  def main(args: Array[String]): Unit = {
  //def config = SocConfig.default.copy(onChipRamSize = 256 kB)
  //def config = SocConfig.default.copy(coreFrequency = 66/4 MHz, onChipRamSize = 4 kB, onChipRamHexFile = "src/main/ressource/hex/demo.hex")
  def config = SocConfig.default.copy(coreFrequency = 66/4 MHz, onChipRamSize = 4 kB, onChipRamHexFile = "/home/clean-mint/spinal/VexRiscvSocSoftware/projects/murax/demo/build/demo.hex")
//  def config = SocConfig.default.copy(coreFrequency = 66/4 MHz, onChipRamSize = 4 kB, onChipRamHexFile = "/home/clean-mint/spinal/VexRiscvSocSoftware/projects/murax/draw/build/draw.hex")

    SimConfig.compile(new Soc(config)).doSimUntilVoid{ dut =>
      val mainClkPeriod = (1e12/dut.config.coreFrequency.toDouble).toLong
      val jtagClkPeriod = mainClkPeriod*4
      val uartBaudRate = 115200
      val uartBaudPeriod = (1e12/uartBaudRate).toLong

      val clockDomain = ClockDomain(dut.io.mainClk, dut.io.asyncReset)
      clockDomain.forkStimulus(mainClkPeriod)
//      clockDomain.forkSimSpeedPrinter(2)

//      val openocd = new Thread  {
//        override def run() = {
//          Thread.sleep(400)
//          scala.sys.process.Process(Seq("src/openocd","-f", "tcl/interface/jtag_tcp.cfg","-c","set MURAX_CPU0_YAML ../VexRiscvLab/cpu0.yaml", "-f","tcl/target/murax.cfg"), new File("../openocd_riscv")).!
//        }
//      }
//      openocd.start()


      val tcpJtag = JtagTcp(
        jtag = dut.io.jtag,
        jtagClkPeriod = jtagClkPeriod
      )

      val uartTx = UartDecoder(
        uartPin = dut.io.uart.txd,
        baudPeriod = uartBaudPeriod
      )

      val uartRx = UartEncoder(
        uartPin = dut.io.uart.rxd,
        baudPeriod = uartBaudPeriod
      )


      val guiThread = fork{
        val guiToSim = mutable.Queue[Any]()

        var ledsValue = 0l
        var switchValue : () => BigInt = null
        val ledsFrame = new JFrame{
          setLayout(new BoxLayout(getContentPane, BoxLayout.Y_AXIS))

          add(new JLedArray(8){
            override def getValue = ledsValue
          })
          add{
            val switches = new JSwitchArray(8)
            switchValue = switches.getValue
            switches
          }

          add(new JButton("Reset"){
            addActionListener(new ActionListener {
              override def actionPerformed(actionEvent: ActionEvent): Unit = {
                println("ASYNC RESET")
                guiToSim.enqueue("asyncReset")
              }
            })
            setAlignmentX(awt.Component.CENTER_ALIGNMENT)
          })
          setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
          pack()
          setVisible(true)

        }

        //Fast refresh
//        clockDomain.onSampling{
//          dut.io.gpioA.read #= (dut.io.gpioA.write.toLong & dut.io.gpioA.writeEnable.toLong) | (switchValue() << 8)
//        }

        //Slow refresh
        while(true){
          sleep(mainClkPeriod*50000)

          val dummy = if(guiToSim.nonEmpty){
            val request = guiToSim.dequeue()
            if(request == "asyncReset"){
              dut.io.asyncReset #= true
              sleep(mainClkPeriod*32)
              dut.io.asyncReset #= false
            }
          }

          dut.io.gpioA.read #= (dut.io.gpioA.write.toLong & dut.io.gpioA.writeEnable.toLong) | (switchValue() << 8)
          ledsValue = dut.io.gpioA.write.toLong
          ledsFrame.repaint()
        }
      }
    }
  }
}
