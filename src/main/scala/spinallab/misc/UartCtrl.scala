package spinallab.misc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3SlaveFactory}
import spinal.lib.com.uart.Uart
import spinal.lib.fsm.{State, StateMachine}

import scala.collection.mutable
import scala.util.Random


class UartCtrl() extends Component{
  val io = new Bundle{
    val apb = slave(Apb3(addressWidth = 4, dataWidth = 32))
    val uart = master(Uart())
  }

  def clockPerBaudType = UInt(12 bits)
  val clockPerBaud = Reg(clockPerBaudType)


  val tx = new Area{
    val valid = RegInit(False)
    val buffer = Reg(Bits(8 bits))
    val state = RegInit(U"0000")
    val tickCounter = Reg(clockPerBaudType) init(0)
    val tick = False
    tickCounter := tickCounter + 1
    when(tickCounter === clockPerBaud){
      tickCounter := 0
      tick := True
    }

    when(valid && tick){
      state := state + 1
      when(state === 1+1+8-1){
        state := 0
        valid := False
      }
    }

    io.uart.txd := Cat(buffer, False, True)(state)
  }

  val rx = new Area{
    val timer = new Area{
      val tick = False
      val sync = False
      val counter = Reg(clockPerBaudType) init(0)
      counter := counter + 1
      when(counter === clockPerBaud){
        counter := 0
        tick := True
      }
      when(sync){
        counter := clockPerBaud |>> 1
      }
    }

    val fsm = new StateMachine{
      val IDLE, START, DATA, STOP = State()
      setEntry(IDLE)

      IDLE.whenIsActive{
        timer.sync := True
        when(!io.uart.rxd){
          goto(START)
        }
      }

      START.whenIsActive{
        when(timer.tick){
          goto(DATA)
        }
      }

      val dataCounter = Reg(UInt(3 bits))
      val dataBuffer = Reg(Bits(8 bits))
      val dataBufferValid = RegInit(False)
      DATA.onEntry(dataCounter := 0)
      DATA.whenIsActive{
        when(timer.tick){
          dataBuffer(dataCounter) := io.uart.rxd
          dataCounter := dataCounter + 1
          when(dataCounter === 7){
            dataBufferValid := True
            goto(STOP)
          }
        }
      }

      STOP.whenIsActive{
        when(timer.tick){
          goto(IDLE)
        }
      }
    }
  }

  val mapping = new Area{
    val factory = Apb3SlaveFactory(io.apb)
    tx.valid setWhen(factory.isWriting(address = 0))
    rx.fsm.dataBufferValid clearWhen(factory.isReading(address = 0))

    factory.write(tx.buffer, address = 0)
    factory.write(clockPerBaud, address = 8)

    factory.read(rx.fsm.dataBuffer, address = 0)
    factory.read(!rx.fsm.isActive(rx.fsm.IDLE) ## tx.valid ## rx.fsm.dataBufferValid, address = 4)
  }
}





object UartCtrl {
  def main(args: Array[String]): Unit = {
    SpinalVhdl(new UartCtrl())
  }
}



object UartCtrlTestbench extends App{
  import spinal.core.sim._
  import spinal.sim._
  SimConfig.withWave.doSim(new UartCtrl()){ dut =>
    dut.clockDomain.forkStimulus(10)
    val baudPeriod = 1000
    dut.io.uart.rxd #= true

    //Send an uart frame to the DUT
    def tbToDut(buffer : Int) = {
      dut.io.uart.rxd #= false
      sleep(baudPeriod)

      (0 to 7).suspendable.foreach{ bitId =>
        dut.io.uart.rxd #= ((buffer >> bitId) & 1) != 0
        sleep(baudPeriod)
      }

      dut.io.uart.rxd #= true
      sleep(baudPeriod)
    }


    val apbDriver = Apb3Driver(dut.io.apb, dut.clockDomain)
    apbDriver.write(0x08, 100-1)


    //Test RXD
    def testTbToDut(data : Int): Unit@suspendable ={
      sleep(baudPeriod*8)
      assert(apbDriver.read(0x04) == 0x00)
      val frameThread = fork(tbToDut(data))
      sleep(baudPeriod*(1 + Random.nextInt(7)))
      assert(apbDriver.read(0x04) == 0x04)
      frameThread.join()
      assert(apbDriver.read(0x04) == 0x01)
      assert(apbDriver.read(0x00) == data)
      assert(apbDriver.read(0x04) == 0x00)
    }

    testTbToDut(0x00)
    testTbToDut(0xFF)
    testTbToDut(0xAA)
    testTbToDut(0xEE)
    testTbToDut(0x42)


    //Fork a process to listen the dut.io.uart.txd pin and fill a queue
    val dutToTbQueue = mutable.Queue[Int]()
    fork{
      sleep(1) //Wait boot signals propagation
      waitUntil(dut.io.uart.txd.toBoolean == true)

      while(true) {
        waitUntil(dut.io.uart.txd.toBoolean == false)
        sleep(baudPeriod/2)

        assert(dut.io.uart.txd.toBoolean == false)
        sleep(baudPeriod)

        var buffer = 0
        (0 to 7).suspendable.foreach{ bitId =>
          if(dut.io.uart.txd.toBoolean)
            buffer |= 1 << bitId
          sleep(baudPeriod)
        }

        assert(dut.io.uart.txd.toBoolean == true)
        dutToTbQueue.enqueue(buffer)
      }
    }

    //Test TXD
    def testDutToTb(data : Int): Unit@suspendable ={
      sleep(baudPeriod*8)
      apbDriver.write(0x00, data)
      fork{
        sleep(baudPeriod*(1 + Random.nextInt(7)))
        assert(apbDriver.read(0x04) == 0x02)
      }
      sleep(baudPeriod*(1+8+1+2))
      assert(apbDriver.read(0x04) == 0x00)
      assert(dutToTbQueue.dequeue() == data)
    }

    testDutToTb(0x00)
    testDutToTb(0xFF)
    testDutToTb(0xAA)
    testDutToTb(0xEE)
    testDutToTb(0x42)
  }
}