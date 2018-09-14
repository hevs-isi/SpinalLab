package spinallab.misc

import spinal.core._
import spinal.lib.Delay

class AsyncAdder(width : Int) extends Component {
  val io = new Bundle {
    val a,b = in UInt(width bits)
    val cin = in Bool

    val s = out UInt(width bits)
    val cout = out Bool
  }
  val resizedSignals = List(io.a, io.b, io.cin.asUInt).map(_.resize(width + 1 bits))
  val result = resizedSignals.reduce(_ + _)
  io.s := result.resized
  io.cout := result.msb
}


class MultiAsyncAdder(asyncAdderWidth : Int, asyncAdderCount : Int) extends Component{
  val width = asyncAdderCount * asyncAdderWidth
  val io = new Bundle {
    val a,b = in UInt(width bits)
    val cin = in Bool

    val s = out UInt(width bits)
    val cout = out Bool
  }

  val asyncAdders = List.fill(asyncAdderCount)(new AsyncAdder(asyncAdderWidth))
  Vec(asyncAdders.map(_.io.a)) <> io.a.subdivideIn(asyncAdderCount slices)
  Vec(asyncAdders.map(_.io.b)) <> io.b.subdivideIn(asyncAdderCount slices)
  Vec(asyncAdders.map(_.io.s)) <> io.s.subdivideIn(asyncAdderCount slices)
  for(i <- 0 until asyncAdderCount){
    asyncAdders(i).io.cin := (if(i == 0) io.cin else asyncAdders(i-1).io.cout)
  }
  io.cout := asyncAdders.last.io.cout
}


class MultiPipelinedAsyncAdder(asyncAdderWidth : Int, asyncAdderCount : Int) extends Component{
  val width = asyncAdderCount * asyncAdderWidth
  val io = new Bundle {
    val a,b = in UInt(width bits)
    val cin = in Bool

    val s = out UInt(width bits)
    val cout = out Bool
  }

  val asyncAdders = List.fill(asyncAdderCount)(new AsyncAdder(asyncAdderWidth))
  val aSlices = io.a.subdivideIn(asyncAdderCount slices)
  val bSlices = io.b.subdivideIn(asyncAdderCount slices)
  val sSlices = io.s.subdivideIn(asyncAdderCount slices)
  for(i <- 0 until asyncAdderCount){
    def preDelay[T <: Data](that : T)  = Delay(that,i)
    def postDelay[T <: Data](that : T) = Delay(that,asyncAdderCount - 1 - i)
    asyncAdders(i).io.cin := (if(i == 0) io.cin else RegNext(asyncAdders(i-1).io.cout))
    asyncAdders(i).io.a   := preDelay(aSlices(i))
    asyncAdders(i).io.b   := preDelay(bSlices(i))
    sSlices(i)            := postDelay(asyncAdders(i).io.s)
  }
  io.cout := asyncAdders.last.io.cout
}


object Adder{
  def main(args: Array[String]): Unit = {
    SpinalVhdl(new AsyncAdder(8))
    SpinalVhdl(new MultiAsyncAdder(8,4))
    SpinalVhdl(new MultiPipelinedAsyncAdder(8,4))
  }
}