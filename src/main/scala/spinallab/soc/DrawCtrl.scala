package spinallab.soc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3SlaveFactory}
import spinal.lib.cpu.riscv.impl.extension.DebugExtension

class DrawCtrl(channelCount : Int,
               signalBitNb : Int,
               memSize : Int,
               timerWidth :Int) extends Component{
  val io = new Bundle{
    val apb = slave(Apb3(8,32))
    val channels = out Bits(channelCount bits)
  }


  val ctrl = new Area{
    val readPtr, writePtr = Counter(memSize)
    val run = RegInit(False)
    val updatePattern = RegInit(False)

    when(updatePattern.rise){
      writePtr.clear()
    }

    val timer = new Area{
      val counter, speed = Reg(UInt(timerWidth bits)) init(0)
      val (counterNext, carry) = AddWithCarry(counter,speed)
      val tick = carry && run
      when(run) {
        counter := counterNext
      }
    }
  }

  val channel = for(i <- 0 until channelCount) yield new Area{
    val ram = Mem(SInt(signalBitNb bits), memSize)

    val interpolator = new Interpolator(
      signalBitNb = signalBitNb,
      oversamplingBitNb = 3,
      sampleCountBitNb = 3
    )
    interpolator.io.valueIn := ram.readSync(ctrl.readPtr)
    interpolator.io.tickIn := RegNext(ctrl.timer.tick)

    val filter = new SigmaDelta(parallelWidth = signalBitNb)
    filter.io.parallelIn <> interpolator.io.valueOut
    filter.io.serialOut <>  io.channels(i)
  }

  when(channel(0).interpolator.io.tickOut){
    ctrl.readPtr.increment()
  }

  val mapping = new Area{
    val apb = Apb3SlaveFactory(io.apb)
    apb.readAndWrite(ctrl.run, 0, 0)
    apb.readAndWrite(ctrl.updatePattern, 0, 1)
    apb.readAndWrite(ctrl.timer.speed, 4)
    for(i <- 0 until channelCount){
      val cmd = apb.createAndDriveFlow(Bits(signalBitNb bits), 8 + i*4)
      when(cmd.valid) {
        channel(i).ram(ctrl.writePtr) := cmd.payload.asSInt
      }
    }
    when(apb.isWriting(8+(channelCount-1)*4)){
      ctrl.writePtr.increment()
    }
  }
}
