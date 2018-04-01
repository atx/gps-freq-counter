
package gfc

import scala.reflect.runtime.universe.{typeOf, TypeTag}

import chisel3._
import chisel3.experimental.MultiIOModule
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, Driver}


abstract class GFCSpec extends ChiselFlatSpec {
  lazy val args = Array("--fint-write-vcd")

  private def genericToClassName[T: TypeTag]() : String = {
    typeOf[T].typeSymbol.name.toString
  }

  def should[T <: Module : TypeTag](description: String,
      dutGen: () => T, testerGen: T => PeekPokeTester[T],
      args: Array[String] = args) = {
    genericToClassName[T]() should description in {
      iotesters.Driver.execute(args, dutGen)(testerGen) should be (true)
    }
  }
}
