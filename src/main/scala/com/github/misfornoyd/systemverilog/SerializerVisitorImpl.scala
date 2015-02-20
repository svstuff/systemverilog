package com.github.misfornoyd.systemverilog

import java.io._
import org.apache.commons.lang3._
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import com.github.misfornoyd.systemverilog.generated._

class SerializerVisitorImpl(parser: Parser, out: OutputStream) extends SerializerVisitor {

  val ruleNames = parser.getRuleNames

  override def visitChildren(ruleNode: RuleNode){
    if ( ruleNode.getChildCount > 0 ) {
      val ruleName = ruleNames(ruleNode.getRuleContext.getRuleIndex)
      write(s"<$ruleName>")
      super.visitChildren(ruleNode)
      write(s"</$ruleName>")
    }
  }

  override def visitTerminal(terminalNode: TerminalNode){
    val tok = terminalNode.getSymbol
    val svtok = tok.asInstanceOf[SVToken]
    val text = StringEscapeUtils.escapeXml10(svtok.getText)
    write(s"<t p='${svtok.line},${svtok.col}'>$text</t>")
  }

  def write(s: String) {
    val bytes = s.getBytes
    out.write(bytes, 0, bytes.length)
  }

  override def start() {
    write("<?xml version='1.0'?>")
    write("<root>")
  }

  override def finish() {
    write("</root>")
    out.close
  }
}
