package com.github.svstuff.systemverilog

import java.io._
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import com.github.svstuff.systemverilog.generated._
import scala.collection.JavaConversions._

/**
  * Find instances of missing being/end blocks.
  * TODO: empty statements?
  */
class BlockStyleVisitor extends SVVisitor {

  val logger = org.slf4j.LoggerFactory.getLogger("BlockStyleVisitor")

  override def start(){
    // No-op
  }

  override def finish(){
    logger.info("BlockStyleVisitor finished.")
  }

  override def visitConditional_statement(ctx: SVParser.Conditional_statementContext) {
    val start = ctx.getStart.asInstanceOf[SVToken]
    if ( start.inMacro ){
      // Skip statements inside macros for now, to avoid spurious output.
      return
    }
    for( stmt <- ctx.statement_or_null().toList ){
      if ( stmt.getStart.getType != LexerTokens.KW_BEGIN ){
        logger.error(s"Missing begin/end in if-statement at ${start.ctx.fileName}:${start.line}")
      }
    }
    visitChildren(ctx)
  }

  override def visitLoop_statement(ctx: SVParser.Loop_statementContext) {
    // Loop statements either have a statement_or_null or statement (foreach).
    val start = ctx.getStart.asInstanceOf[SVToken]
    if ( start.inMacro ){
      // Skip statements inside macros for now, to avoid spurious output.
      return
    }
    val stmt = (
      if ( ctx.statement_or_null() != null )
        ctx.statement_or_null
      else
        ctx.statement
    )
    if ( stmt.getStart.getType != LexerTokens.KW_BEGIN ){
      logger.error(s"Missing begin/end in loop-statement at ${start.ctx.fileName}:${start.line}")
    }
    visitChildren(ctx)
  }
}
