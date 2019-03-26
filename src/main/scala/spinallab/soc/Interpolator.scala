package spinallab.soc

import spinal.core._

class Interpolator(signalBitNb : Int,
                   oversamplingBitNb : Int,
                   sampleCountBitNb : Int) extends Component{

  val io = new Bundle{
    val tickIn = in Bool
    val tickOut = out Bool

    val valueIn = in SInt(signalBitNb bits)
    val valueOut = out UInt(signalBitNb bits)
  }


  val trigger = new Area{
    val counter = Reg(UInt(sampleCountBitNb bits)) init(0)
    val newPolynome = False
    when(io.tickIn) {
      counter := counter + 1
      when(counter === counter.maxValue){
        newPolynome := True
      }
    }
    io.tickOut := newPolynome
  }

  val shiftRegister = new Area{
    val samples = Vec(Reg(SInt(signalBitNb bits)) init(0), 4)
    when(trigger.newPolynome){
      for(i <- 0 to 2){
        samples(i) := samples(i + 1)
      }
      samples(3) := io.valueIn
    }
  }

  val coeffBitNb = signalBitNb+4
  val coefficients = new Area{
    import shiftRegister.samples
    val a = - samples(0).resize(coeffBitNb) + 3*samples(1) - 3*samples(2) + samples(3)
    val b = 2*samples(0).resize(coeffBitNb) - 5*samples(1) + 4*samples(2) - samples(3)
    val c = - samples(0).resize(coeffBitNb)                +   samples(2)
    val d =                                     samples(1).resize(coeffBitNb)
  }

  val polynom = new Area{
    val m = oversamplingBitNb
    import coefficients.{a, b, c, d}
    val x, u, v, w, y = Reg(SInt(coeffBitNb + 3*m + 8 bits))
    when(io.tickIn) {
      when(trigger.newPolynome) {
        x := (d << (3 * m + 1)).resized
        u := (a + (b << m) + (c << (2 * m))).resized
        v := (6 * a + (b << (m + 1))).resized
        w := (6 * a).resized
        y := (d).resized
      } otherwise {
        val xNext = x + u
        y := (xNext >> (3 * m + 1)).resized
        x := xNext
        u := u + v
        v := v + w
      }
    }
  }
  io.valueOut := polynom.y.asUInt.resized + (1 << (signalBitNb - 1))
}
