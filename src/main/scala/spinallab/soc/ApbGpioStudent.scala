package spinallab.soc

import spinal.core._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3SlaveFactory}
import spinal.lib.io.TriStateArray
import spinal.lib.{master, slave}


//Memory mapping =>
// 0x0 => read/write
// 0x4 => direction, 1 => output
class ApbGpio(gpioWidth: Int) extends Component {
  val io = new Bundle {
    val apb  = slave(Apb3(addressWidth = 4, dataWidth = 32))
    val gpio = master(TriStateArray(gpioWidth bits))
  }

  //TODO
  io.gpio.writeEnable := 0x000000FF
  io.gpio.write := io.gpio.read |>> 8
  io.apb.PRDATA := 0
  io.apb.PREADY := True
}
