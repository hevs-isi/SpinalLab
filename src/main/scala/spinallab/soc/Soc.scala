package spinallab.soc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.misc._
import spinal.lib.bus.simple._
import spinal.lib.com.jtag.Jtag
import spinal.lib.com.uart._
import spinal.lib.io.{InOutWrapper, TriStateArray}
import vexriscv.demo._
import vexriscv.plugin._
import vexriscv._

import scala.collection.mutable.ArrayBuffer

/**
 * Created by PIC32F_USER on 28/07/2017.
 *
 * Murax is a very light SoC which could work without any external component.
 * - ICE40-hx8k + icestorm =>  53 Mhz, 2142 LC
 * - 0.37 DMIPS/Mhz
 * - 8 kB of on-chip ram
 * - JTAG debugger (eclipse/GDB/openocd ready)
 * - Interrupt support
 * - APB bus for peripherals
 * - 32 GPIO pin
 * - one 16 bits prescaler, two 16 bits timers
 * - one UART with tx/rx fifo
 */


case class SocConfig(coreFrequency : HertzNumber,
                     onChipRamSize      : BigInt,
                     onChipRamHexFile   : String,
                     gpioWidth          : Int,
                     uartCtrlConfig     : UartCtrlMemoryMappedConfig,
                     cpuPlugins         : ArrayBuffer[Plugin[VexRiscv]]){
}

object SocConfig{
  def default =  SocConfig(
    coreFrequency         = 66/4 MHz,
    onChipRamSize         = 8 kB,
    onChipRamHexFile      = null,
    gpioWidth = 32,
    cpuPlugins = ArrayBuffer( //DebugPlugin added by the toplevel
      new IBusSimplePlugin(
        resetVector = 0x80000000l,
        catchAccessFault = false,
        cmdForkOnSecondStage = false,
        cmdForkPersistence = true
      ),
      new DBusSimplePlugin(
        catchAddressMisaligned = false,
        catchAccessFault = false,
        earlyInjection = false
      ),
      new CsrPlugin(CsrPluginConfig.smallest(mtvecInit = 0x80000020l)),
      new DecoderSimplePlugin(
        catchIllegalInstruction = false
      ),
      new RegFilePlugin(
        regFileReadyKind = plugin.SYNC,
        zeroBoot = false
      ),
      new IntAluPlugin,
      new SrcPlugin(
        separatedAddSub = false,
        executeInsertion = false
      ),
      new LightShifterPlugin,
      new HazardSimplePlugin(
        bypassExecute = false,
        bypassMemory = false,
        bypassWriteBack = false,
        bypassWriteBackBuffer = false,
        pessimisticUseSrc = false,
        pessimisticWriteRegFile = false,
        pessimisticAddressMatch = false
      ),
      new BranchPlugin(
        earlyBranch = false,
        catchAddressMisaligned = false
      ),
      new YamlPlugin("cpu0.yaml")
    ),
    uartCtrlConfig = UartCtrlMemoryMappedConfig(
      uartCtrlConfig = UartCtrlGenerics(
        dataWidthMax      = 8,
        clockDividerWidth = 20,
        preSamplingSize   = 1,
        samplingSize      = 3,
        postSamplingSize  = 1
      ),
      initConfig = UartCtrlInitConfig(
        baudrate = 115200,
        dataLength = 7,  //7 => 8 bits
        parity = UartParityType.NONE,
        stop = UartStopType.ONE
      ),
      busCanWriteClockDividerConfig = false,
      busCanWriteFrameConfig = false,
      txFifoDepth = 16,
      rxFifoDepth = 16
    )

  )

  def fast = {
    val config = default

    //Replace HazardSimplePlugin to get datapath bypass
    config.cpuPlugins(config.cpuPlugins.indexWhere(_.isInstanceOf[HazardSimplePlugin])) = new HazardSimplePlugin(
      bypassExecute = true,
      bypassMemory = true,
      bypassWriteBack = true,
      bypassWriteBackBuffer = true
    )
//    config.cpuPlugins(config.cpuPlugins.indexWhere(_.isInstanceOf[LightShifterPlugin])) = new FullBarrielShifterPlugin()

    config
  }
}


case class Soc(config : SocConfig) extends Component{
  import config._

  val io = new Bundle {
    //Clocks / reset
    val asyncReset = in Bool
    val mainClk = in Bool

    //Main components IO
    val jtag = slave(Jtag())

    //Peripherals IO
    val gpioA = master(TriStateArray(gpioWidth bits))
    val uart = master(Uart())
    val drawX, drawY = out Bool
  }


  val resetCtrlClockDomain = ClockDomain(
    clock = io.mainClk,
    config = ClockDomainConfig(
      resetKind = BOOT
    )
  )

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val mainClkResetUnbuffered  = False

    //Implement an counter to keep the reset axiResetOrder high 64 cycles
    // Also this counter will automatically do a reset when the system boot.
    val systemClkResetCounter = Reg(UInt(6 bits)) init(0)
    when(systemClkResetCounter =/= U(systemClkResetCounter.range -> true)){
      systemClkResetCounter := systemClkResetCounter + 1
      mainClkResetUnbuffered := True
    }
    when(BufferCC(io.asyncReset)){
      systemClkResetCounter := 0
    }

    //Create all reset used later in the design
    val mainClkReset = RegNext(mainClkResetUnbuffered)
    val systemReset  = RegNext(mainClkResetUnbuffered)
  }


  val systemClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(coreFrequency)
  )

  val debugClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.mainClkReset,
    frequency = FixedFrequency(coreFrequency)
  )

  val system = new ClockingArea(systemClockDomain) {
    //***************************************
    //****** Interconnects utilities ********
    //***************************************
    val interconnect = PipelinedMemoryBusInterconnect()
    val apbMapping = ArrayBuffer[(Apb3, SizeMapping)]()

    val mainBus = PipelinedMemoryBus(addressWidth = 32, dataWidth = 32)
    interconnect.addSlave(mainBus, DefaultMapping)


    //*********************************************
    //****** CPU instanciation and binding ********
    //*********************************************
    //Instanciate the CPU
    val cpu = new VexRiscv(
      config = VexRiscvConfig(
        plugins = cpuPlugins += new DebugPlugin(debugClockDomain)
      )
    )

    //Checkout plugins used to instantiate the CPU to connect them to the SoC
    val timerInterrupt = False
    val externalInterrupt = False
    val dBus = PipelinedMemoryBus(mainBus.config)
    val iBus = PipelinedMemoryBus(mainBus.config)
    for(plugin <- cpu.plugins) plugin match{
      case plugin : IBusSimplePlugin => iBus << plugin.iBus.toPipelinedMemoryBus()
      case plugin : DBusSimplePlugin => {
        val buffer = plugin.dBus.toPipelinedMemoryBus()
        dBus.cmd << buffer.cmd.halfPipe()
        dBus.rsp >> buffer.rsp
      }
      case plugin : CsrPlugin        => {
        plugin.externalInterrupt := externalInterrupt
        plugin.timerInterrupt := timerInterrupt
      }
      case plugin : DebugPlugin         => plugin.debugClockDomain{
        resetCtrl.systemReset setWhen(RegNext(plugin.io.resetOut))
        io.jtag <> plugin.io.bus.fromJtag()
      }
      case _ =>
    }


    //******************************
    //****** MainBus slaves ********
    //******************************
    val ram = new Spartan3PipelinedMemoryBusRam(
      onChipRamSize = onChipRamSize,
      onChipRamHexFile = onChipRamHexFile,
      pipelinedMemoryBusConfig = mainBus.config
    )
    interconnect.addSlave(ram.io.bus, SizeMapping(0x80000000l, onChipRamSize))

    val apbBridge = new PipelinedMemoryBusToApbBridge(
      apb3Config = Apb3Config(
        addressWidth = 20,
        dataWidth = 32
      ),
      pipelineBridge = true,
      pipelinedMemoryBusConfig = mainBus.config
    )
    interconnect.addSlave(apbBridge.io.pipelinedMemoryBus, SizeMapping(0xF0000000l, 1 MB))


    //**********************************
    //******** APB slaves *********
    //**********************************

    //Add the GPIO controller
    val gpioACtrl = new ApbGpio(gpioWidth = gpioWidth) //TODO fill Apb3Gpio implementation
    apbMapping += gpioACtrl.io.apb -> (0x00000, 4 kB)
    io.gpioA <> gpioACtrl.io.gpio

    //Add the UART controller TODO
    io.uart.txd := True //TODO

    //Add the Timer
    val timer = new MuraxApb3Timer()
    apbMapping += timer.io.apb     -> (0x20000, 4 kB)
    timerInterrupt setWhen(timer.io.interrupt)

    //Add the DrawCtrl TODO
    val drawCtrl = new DrawCtrl(
      channelCount = 2,
      signalBitNb = 16,
      memSize = 16,
      timerWidth  = 16
    )
    apbMapping += drawCtrl.io.apb  -> (0x30000, 4 kB)
    drawCtrl.io.channels(0) <> io.drawX
    drawCtrl.io.channels(1) <> io.drawY

    //******** Memory interconnect finalisation *********
    val apbDecoder = Apb3Decoder(
      master = apbBridge.io.apb,
      slaves = apbMapping
    )

    //Define which bus can access to which slave
    interconnect.addMasters(
      dBus   -> List(mainBus),
      iBus   -> List(mainBus),
      mainBus-> List(ram.io.bus, apbBridge.io.pipelinedMemoryBus)
    )
  }
}



//Will blink led and echo UART RX to UART TX   (in the verilator sim, type some text and press enter to send UART frame to the Murax RX pin)
object SocEbs{
  def main(args: Array[String]) {
    SpinalVhdl(InOutWrapper{
      val m = Soc(SocConfig.default.copy(onChipRamSize = 4 kB/*, onChipRamHexFile = "src/main/ressource/hex/muraxDemo.hex"*/))
      m.rework{
        ///Fix ram inferation
        //m.system.ram.ram.addAttribute("ram_style", "block")
        //m.system.ram.ram.addAttribute("rom_style", "block")

        //Reduce GPIO width
        m.io.gpioA.setAsDirectionLess().unsetName().allowDirectionLessIo
        val gpioA = master(TriStateArray(4 bits)).setName("io_gpioA")
        gpioA.writeEnable := m.io.gpioA.writeEnable.resized
        gpioA.write := m.io.gpioA.write.resized
        m.io.gpioA.read := gpioA.read.resized

        //adapt asyncReset polarity
        m.io.asyncReset.setAsDirectionLess().allowDirectionLessIo := !(in(Bool).setName("io_asyncResetN"))

        //clock divider
        ClockDomain(in(Bool).setName("io_mainClk")) {
          val counter = Reg(UInt(2 bits)) randBoot()
          counter := counter + 1
          m.io.mainClk.unsetName().setAsDirectionLess().allowDirectionLessIo := counter.msb
        }
      }
      m
    })
  }
}
