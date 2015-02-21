package com.github.misfornoyd.systemverilog

import com.github.misfornoyd.systemverilog.generated._

abstract class SVVisitor extends SVParserBaseVisitor[Unit] {

  def start()
  def finish()

}
