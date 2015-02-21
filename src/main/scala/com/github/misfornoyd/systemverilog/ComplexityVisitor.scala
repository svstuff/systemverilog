package com.github.misfornoyd.systemverilog

import java.io._
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import com.github.misfornoyd.systemverilog.generated._

/**
  * Write complexity metrics as a YAML sequence.
  * TODO: include some useful metrics, like cyclomatic complexity.
  */
class ComplexityVisitor(out: Writer) extends SVParserBaseVisitor[Unit] {

  val scope = new collection.mutable.Stack[String]

  override def visitClass_declaration(ctx: SVParser.Class_declarationContext) {
    scope.push(ctx.class_identifier(0).getStart.getText)
    super.visitClass_declaration(ctx)
    scope.pop
  }

  override def visitPackage_declaration(ctx: SVParser.Package_declarationContext) {
    scope.push(ctx.package_identifier(0).getStart.getText)
    super.visitPackage_declaration(ctx)
    scope.pop
  }

  override def visitFunction_declaration(ctx: SVParser.Function_declarationContext) {
    val start = ctx.getStart
    val len = ctx.getStop.getLine - start.getLine
    val name = ctx.function_identifier(0).getStart.getText
    val qualified = scope.mkString("::")
    out.write(s"{type:f, name:$qualified::$name, start:${start.getLine}, len:${len}}\n")
  }

  override def visitTask_declaration(ctx: SVParser.Task_declarationContext) {
    val start = ctx.getStart
    val len = ctx.getStop.getLine - start.getLine
    val name = ctx.task_identifier(0).getStart.getText
    val qualified = scope.mkString("::")
    out.write(s"{type:t, name:$qualified::$name, start:${start.getLine}, len:${len}}\n")
  }

}
