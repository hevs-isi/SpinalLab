package spinallab.misc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config, Apb3SlaveFactory}
import spinal.lib.io.TriStateArray


object ApbGpio{
  val dataAddress = 0
  val directionAddress = 4

  val apbConfig = Apb3Config(
    addressWidth = 4,
    dataWidth = 32,
    selWidth = 1,
    useSlaveError  = true
  )

  def main(args: Array[String]): Unit = {
    SpinalVhdl(new ApbGpio(8))
    SpinalVhdl(new ApbGpioUsingFactory(8))
  }
}

case class ApbGpioInterface(gpioWidth : Int) extends Bundle{
  val apb = slave(Apb3(ApbGpio.apbConfig))
  val gpio = master(TriStateArray(gpioWidth))
}

class ApbGpio(gpioWidth : Int) extends Component{
  val io = ApbGpioInterface(gpioWidth)

  val output = Reg(Bits(gpioWidth bits))
  val direction = RegInit(B(0, gpioWidth bits))

  io.gpio.writeEnable := direction
  io.gpio.write := output

  io.apb.PREADY := True
  io.apb.PSLVERROR := False
  io.apb.PRDATA := io.gpio.read.resized

  when(io.apb.PSEL.lsb && io.apb.PENABLE && io.apb.PWRITE) {
    switch(io.apb.PADDR) {
      is(ApbGpio.dataAddress) {
        output := io.apb.PWDATA.resized
      }
      is(ApbGpio.directionAddress) {
        direction := io.apb.PWDATA.resized
      }
    }
  }
}



class ApbGpioUsingFactory(gpioWidth : Int) extends Component{
  val io = ApbGpioInterface(gpioWidth)

  val factory   = Apb3SlaveFactory(io.apb)
  io.gpio.write := factory.createReadAndWrite(Bits(gpioWidth bits), address = ApbGpio.dataAddress)
  io.gpio.writeEnable := factory.createWriteOnly(Bits(gpioWidth bits), address = ApbGpio.directionAddress) init(0)
  factory.read(io.gpio.read, address = ApbGpio.dataAddress)
}


object ApbGpioTestbench extends App{
  import spinal.core.sim._

  def testDut(io : ApbGpioInterface): Unit@suspendable ={
    val clockDomain = io.component.clockDomain
    clockDomain.forkStimulus(10)
    val apbDriver = Apb3Driver(io.apb, clockDomain)

    //Stimulus
    clockDomain.waitSampling()
    assert(io.gpio.writeEnable.toBigInt == 0)
    io.gpio.read #= 0xE5
    apbDriver.write(ApbGpio.directionAddress, 0x0F)
    apbDriver.write(ApbGpio.dataAddress, 0xAA)

    //Checks
    clockDomain.waitSampling()
    assert(io.gpio.writeEnable.toInt == 0x0F)
    assert(io.gpio.write.toInt == 0xAA)
    assert(apbDriver.read(ApbGpio.dataAddress) == 0xE5)
  }

  SimConfig.withWave.doSim(new ApbGpio(8)){dut => testDut(dut.io)}
  SimConfig.withWave.doSim(new ApbGpioUsingFactory(8)){dut => testDut(dut.io)}
}