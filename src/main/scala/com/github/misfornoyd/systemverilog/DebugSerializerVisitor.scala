package com.github.misfornoyd.systemverilog

import java.io._
import org.apache.commons.lang3._
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import com.github.misfornoyd.systemverilog.generated._

/**
  * Serializer outputting a parsetree useful for debugging.
  * This can be inefficient and output more stuff since it must be explicitly enabled.
  */
class DebugSerializerVisitor(parser: Parser, out: OutputStream) extends SerializerVisitor {

  val ruleNames = parser.getRuleNames
  var level = 0

  override def visitChildren(ruleNode: RuleNode){
    if ( ruleNode.getChildCount > 0 ) {
      val ruleName = ruleNames(ruleNode.getRuleContext.getRuleIndex)
      writeIndent
      write(s"<$ruleName>\n")
      level += 1
      super.visitChildren(ruleNode)
      level -= 1
      writeIndent
      write(s"</$ruleName>\n")
    }
  }

  override def visitTerminal(terminalNode: TerminalNode){
    val tok = terminalNode.getSymbol
    val svtok = tok.asInstanceOf[SVToken]
    writeIndent
    val text = StringEscapeUtils.escapeXml10(svtok.getText)
    write(s"<t p='${svtok.line},${svtok.col}'>$text</t>\n")
  }

  def writeIndent(){
    write("  " * level)
  }

  def write(s: String) {
    val bytes = s.getBytes
    out.write(bytes, 0, bytes.length)
  }

  override def start() {
    write("<?xml version='1.0'?>\n")
    write("<root>\n")
  }

  override def finish() {
    write("</root>")
    out.close
  }
}
