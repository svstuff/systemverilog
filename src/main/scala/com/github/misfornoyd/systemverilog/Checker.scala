package com.github.misfornoyd.systemverilog

import collection.mutable.Map
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import org.antlr.v4.runtime.misc._

import generated._

sealed class Checker(writer: java.io.PrintWriter) extends SVParserBaseVisitor[Unit] with com.typesafe.scalalogging.slf4j.Logging {

  object IdVisitor extends SVParserBaseVisitor[String] {
    override def visitIdentifier(ctx: SVParser.IdentifierContext) : String = {
      ctx.getChild(0).getText
    }
  }

/* TODO visit these and report stats

  : blocking_assignment SEMI
  | nonblocking_assignment SEMI
  | procedural_continuous_assignment SEMI
  | case_statement
  | conditional_statement
  | inc_or_dec_expression SEMI
  | subroutine_call_statement
  | disable_statement
  | event_trigger
  | loop_statement
  | jump_statement
  | par_block
  | procedural_timing_control_statement
  | seq_block
  | wait_statement
  | procedural_assertion_statement
  | clocking_drive SEMI
  | randsequence_statement
  | randcase_statement
  | expect_property_statement
*/

  class StmtVisitor extends SVParserBaseVisitor[Unit] {

    val stmt = Map[String, Int]()

    override def visitStatement(ctx: SVParser.StatementContext){
      // TODO use more idiomatic scala for applying update
      stmt += ( "total" -> (1 + stmt.getOrElse("total", 0)) )
      visitChildren(ctx)
    }
    override def visitLoop_statement(ctx: SVParser.Loop_statementContext){
      stmt += ( "loop" -> (1 + stmt.getOrElse("loop", 0)) )
      visitChildren(ctx)
    }
    override def visitSubroutine_call_statement(ctx: SVParser.Subroutine_call_statementContext){
      stmt += ( "subroutine_call" -> (1 + stmt.getOrElse("subroutine_call", 0)) )
      visitChildren(ctx)
    }
  }

  override def visitFunction_declaration(ctx: SVParser.Function_declarationContext){
    val name = IdVisitor.visit(ctx.function_identifier(0))
    writer.print(s"<func><name>${name}</name>")
    writer.print(s"<start>${ctx.getStart.getLine}</start>")
    writer.print(s"<stop>${ctx.getStop.getLine}</stop>")
    writer.print(s"<stmt>")
    val stmt = new StmtVisitor()
    stmt.visit(ctx)
    for ((key, value) <- stmt.stmt) {
      writer.print(s"<${key}>${value}</${key}>")
    }
    writer.print(s"</stmt>")
    writer.print(s"</func>")
  }

  override def visitClass_declaration(ctx: SVParser.Class_declarationContext){
    val name = IdVisitor.visit(ctx.class_identifier(0))
    writer.print(s"<class><name>${name}</name>")
    writer.print(s"<start>${ctx.getStart.getLine}</start>")
    writer.print(s"<stop>${ctx.getStop.getLine}</stop>")
    visitChildren(ctx)
    writer.print(s"</class>")
  }
}
