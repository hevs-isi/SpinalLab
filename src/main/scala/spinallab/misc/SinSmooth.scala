package spinallab.misc

import spinal.core._

class SinSmooth(phaseBitNb : Int,
                signalBitNb : Int,
                tableAddressBitNb : Int,
                oversamplingBitNb : Int) extends Component {
  val io = new Bundle {
    val step      = in  UInt(phaseBitNb bits)

    val sawtooth  = out UInt(signalBitNb bits)
    val square    = out UInt(signalBitNb bits)
    val triangle  = out UInt(signalBitNb bits)
    val sine      = out UInt(signalBitNb bits)
  }

  val phase = Reg(UInt(phaseBitNb bits)) init(0)
  phase := phase + io.step

  val sawToothShift = phaseBitNb - signalBitNb
  io.sawtooth := (if(sawToothShift > 0) phase >> sawToothShift else phase << -sawToothShift)
  io.square := U(io.square.range -> io.sawtooth.msb)

  io.triangle := io.sawtooth.msb.mux(
    False ->  io.sawtooth,
    True  -> ~io.sawtooth
  )

  val sineTable = new Area{
    val romSize = 1 << tableAddressBitNb
    val rom = Mem((0 until romSize).map(i => {
      val angle = Math.PI/2.0*i/romSize
      val sin = Math.sin(angle)*(1 << (signalBitNb-1))
      U(sin.toInt, signalBitNb bits)
    }))

    val addressBeforeCorrection = phase(phaseBitNb-2-tableAddressBitNb, tableAddressBitNb bits)
    val addressAfterCorrection = phase(phaseBitNb-2).mux(
      False ->  addressBeforeCorrection,
      True  -> (romSize-addressBeforeCorrection).resize(tableAddressBitNb)
    )

    val sample = rom(addressAfterCorrection).asSInt
    when(phase(phaseBitNb-2) && addressBeforeCorrection === 0){
      sample := sample.maxValue
    }

    val sine = phase(phaseBitNb-1).mux(
      False ->  sample,
      True  -> -sample
    )
  }

  val interpolatorTrigger = new Area{
    val sampleCountBitNb = phaseBitNb-2-tableAddressBitNb
    val en = True
    val counter = Reg(UInt(sampleCountBitNb bits)) init(0)
    val newPolynome = False
    when(en) {
      counter := counter + 1
      when(counter === counter.maxValue){
        newPolynome := True
      }
    }
  }

  val interpolatorShiftRegister = new Area{
    val samples = Vec(Reg(SInt(signalBitNb bits)) init(0), 4)
    when(interpolatorTrigger.newPolynome){
      for(i <- 0 to 2){
        samples(i) := samples(i + 1)
      }
      samples(3) := sineTable.sine
    }
  }

  val coeffBitNb = signalBitNb+4
  val interpolatorCoefficients = new Area{
    import interpolatorShiftRegister.samples
    val a = - samples(0).resize(coeffBitNb) + 3*samples(1) - 3*samples(2) + samples(3)
    val b = 2*samples(0).resize(coeffBitNb) - 5*samples(1) + 4*samples(2) - samples(3)
    val c = - samples(0).resize(coeffBitNb)                +   samples(2)
    val d =                                     samples(1).resize(coeffBitNb)
  }

  val interpolatorPolynom = new Area{
    val en = True
    val m = oversamplingBitNb
    import interpolatorCoefficients.{a, b, c, d}
    val x, u, v, w, y = Reg(SInt(coeffBitNb + 3*m + 8 bits))
    when(en) {
      when(interpolatorTrigger.newPolynome) {
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

  io.sine := interpolatorPolynom.y.asUInt.resized + (1 << (signalBitNb - 1))
}


object SinSmooth {
  def main(args: Array[String]) {
    SpinalVhdl(new SinSmooth(
      phaseBitNb = 8,
      signalBitNb = 16,
      tableAddressBitNb = 3,
      oversamplingBitNb = 3
    ))
  }
}

