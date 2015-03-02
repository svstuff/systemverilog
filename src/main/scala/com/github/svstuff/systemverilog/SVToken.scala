package com.github.svstuff.systemverilog

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenSource

import generated._

sealed abstract class SVToken (val tokindex:Int, val toktype:Int, val ctx:Context, val line:Int, val col:Int) extends org.antlr.v4.runtime.Token {

  def isEOF() : Boolean = {
    toktype == Token.EOF
  }

  override def getChannel() : Int = {
    Token.DEFAULT_CHANNEL
  }

  override def getInputStream() : CharStream = {
    assert(false)
    null
  }

  override def getTokenSource() : TokenSource = {
    assert(false)
    null
  }

  override def getStartIndex() : Int = {
    assert(false)
    -1
  }

  override def getStopIndex() : Int = {
    assert(false)
    -1
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

  override def toString() : String = {
    if ( isEOF ){
      "%s(%s,%d,%d,%s)".format("EOF", "<root>", line, col, "<eof>")
    }else{
      "%s(%s,%d,%d,%s)".format(LexerTokens.tokenNames(toktype), ctx.shortId(), line, col, getText)
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
    if ( isEOF ){
      "<eof>"
    }else{
      LexerTokens.tokenConstText(toktype)
    }
  }
}
