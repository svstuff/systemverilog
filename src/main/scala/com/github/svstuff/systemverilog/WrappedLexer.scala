package com.github.svstuff.systemverilog

import org.antlr.v4.runtime._
import java.util.concurrent._

sealed class WrappedLexer(tokens : BlockingQueue[SVToken]) extends TokenSource {

  override def nextToken() : Token = {
    try {
      tokens.take()
    } catch {
      case e : InterruptedException => throw new CancellationException()
    }
  }

  override def setTokenFactory(factory : TokenFactory[_]){
  }

  override def getTokenFactory() : TokenFactory[_] = {
    null
  }

  override def getSourceName() : String = {
    "yo"
  }

  override def getInputStream() : CharStream = {
    null
  }

  override def getCharPositionInLine() : Int = {
    0
  }

  override def getLine() : Int = {
    0
  }

}
