
package gfc

import chisel3._
import firrtl._
import java.io._

object Main extends App {
  val defaultArgs = Array("--target-dir", "build/")
  chisel3.Driver.execute(defaultArgs ++ args, () => new Top)
}
