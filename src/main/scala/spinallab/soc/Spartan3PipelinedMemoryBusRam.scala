package spinallab.soc

import spinal.core._
import spinal.lib.bus.simple.{PipelinedMemoryBus, PipelinedMemoryBusConfig}
import spinal.lib.misc.HexTools
import spinal.lib._


case class Spartan3PipelinedMemoryBusRam(onChipRamSize : BigInt, onChipRamHexFile : String, pipelinedMemoryBusConfig : PipelinedMemoryBusConfig) extends Component{
  val io = new Bundle{
    val bus = slave(PipelinedMemoryBus(pipelinedMemoryBusConfig))
  }

  val ram = Mem(Bits(32 bits), onChipRamSize / 4)
  ram.write(
    address = (io.bus.cmd.address >> 2).resized,
    data  = io.bus.cmd.data,
    enable  = io.bus.cmd.valid && io.bus.cmd.write,
    mask  = io.bus.cmd.mask
  )
  io.bus.cmd.ready := True

  io.bus.rsp.valid := RegNext(io.bus.cmd.fire && !io.bus.cmd.write) init(False)
  io.bus.rsp.data := ram.readSync((io.bus.cmd.address >> 2).resized)

  if(onChipRamHexFile != null){
    HexTools.initRam(ram, onChipRamHexFile, 0x80000000l)
  }
}