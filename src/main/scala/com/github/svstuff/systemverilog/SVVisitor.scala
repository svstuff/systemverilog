package com.github.svstuff.systemverilog

import com.github.svstuff.systemverilog.generated._

abstract class SVVisitor extends SVParserBaseVisitor[Unit] {

  def start()
  def finish()

}
