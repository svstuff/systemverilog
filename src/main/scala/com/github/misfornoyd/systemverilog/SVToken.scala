package com.github.misfornoyd.systemverilog

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenSource

import generated._

sealed abstract class SVToken (val tokindex:Int, val toktype:Int, val ctx:Context, val line:Int, val col:Int) extends org.antlr.v4.runtime.Token {

  def isError() : Boolean = {
    toktype != LexerTokens.ERROR
  }

  override def getChannel() : Int = {
    Token.DEFAULT_CHANNEL
  }

  override def getInputStream() : CharStream = {
    null
  }

  override def getTokenSource() : TokenSource = {
    null
  }

  override def getTokenIndex() : Int = {
    tokindex
  }

  override def getType() : Int = {
    toktype
  }

  override def getCharPositionInLine() : Int = {
    col
  }

  override def getLine() : Int = {
    line
  }

  override def getStartIndex() : Int = {
    -1
  }

  override def getStopIndex() : Int = {
    -1
  }

  override def toString() : String = {
    if ( toktype != Token.EOF ){
      "%s(%s,%d,%d,%s)".format(LexerTokens.tokenNames(toktype), ctx.getShortId(), line, col, getText())
    }else{
      "%s(%s,%d,%d,%s)".format("EOF", "<root>", line, col, "<eof>")
    }
  }
}

class SVTokenText(tokindex:Int, toktype:Int, ctx:Context, line:Int, col:Int, val text:String) extends SVToken(tokindex, toktype, ctx, line, col) {
  override def getText() : String = {
    text
  }
}

class SVTokenConst(tokindex:Int, toktype:Int, ctx:Context, line:Int, col:Int) extends SVToken(tokindex, toktype, ctx, line, col) {
  override def getText() : String = {
    if ( toktype != Token.EOF ){
      LexerTokens.tokenConstText(toktype)
    }else{
      "<eof>"
    }
  }
}
