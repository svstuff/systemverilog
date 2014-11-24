package com.github.misfornoyd.systemverilog

import io.Source
import collection.JavaConversions._

import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import org.antlr.v4.runtime.misc._

import java.util.concurrent._
import java.io.File
import java.io.FileWriter

import generated._

object Driver {

  val version = "0.1"

  def main( args: Array[String] ) {
    try {
      parse(args(0))
    } catch {
      case e: LexerError => // swallow lexer errors, since these have already been reported.
    }
  }
  def getLogger( logLevel: String ) : org.slf4j.Logger = {
    // Set the default log level and formatting before any loggers are created.
    if ( logLevel == "" ) {
      System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG")
    } else {
      System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel)
    }
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "FALSE")
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "TRUE")
    org.slf4j.LoggerFactory.getLogger("Driver")
  }
  def toAbsolutePaths( prefix: String, paths: List[String] ) : List[String] = {
    paths.map( (path) => {
      if ( path(0) == '/' ) {
        path
      } else{
        "%s/%s".format(prefix, path)
      }
    })
  }
  def parse( projectPath: String ) {
    println("SVPARSE version %s.\nReading project file: %s".format(version, projectPath))

    val extra = util.Properties.envOrNone("SVPARSE_EXTRA") match {
      case Some(extraPath) => {
        println(s"Loading extra parameters from ${extraPath}")
        xml.XML.loadFile(extraPath)
      }
      case None => <root/>
    }
    val extraDebugOptions = (extra \\ "debug").toList.map(_.text)
    val extraLogLevel = (extra \ "logLevel").text

    val project = xml.XML.loadFile(projectPath)

    val logger =
      if ( extraLogLevel.isEmpty ) {
        getLogger((project \ "logLevel").text)
      } else {
        println(s"Overriding project log level to ${extraLogLevel}")
        getLogger(extraLogLevel)
      }

    // NOTE: from this point on use the logger for output

    val projectDebugOptions = (project \\ "debug").toList.map(_.text)
    val debugOptions = extraDebugOptions ++ projectDebugOptions

    val addProjectDirToIncdirs = debugOptions.contains("add_project_dir_to_incdirs")
    val lexerOnly = debugOptions.contains("lexer_only")
    val skipTokenEnqueue = lexerOnly
    val printTokens = debugOptions.contains("print_tokens")

    // get the directory of the project file and prefix this on all _relative_ file paths
    val dirpre = new File(new File(projectPath).getAbsolutePath()).getParentFile().toString

    val incdirsRaw = collection.mutable.ListBuffer.empty[String]
    if ( addProjectDirToIncdirs ) {
      incdirsRaw += dirpre
    }
    incdirsRaw ++= (project \\ "incdir").map(_.text)

    val incdirs = toAbsolutePaths(dirpre, incdirsRaw.toList)
    val sources = toAbsolutePaths(dirpre, (project \\ "source").map( _.text ).toList)
    val defines = (project \\ "define").map( d => ( (d \ "name").text -> (d \ "value").text ) ).toMap

    val tokenQueueCapacity = 1000
    val tokens = new ArrayBlockingQueue[SVToken](tokenQueueCapacity)

    /*
    The below is quite ugly, and should probably be redesigned. I'm concerned that using actors
    with very fine grained messages (tokens) will be too slow, hence the following pattern instead.

    An error in the lexer thread can be signalled to the parser by producing a special token. This
    will cause the parser thread to bail automatically. However, the lexer thread must interrupt the
    parser thread on unexpected (programmer error) exceptions.

    An error in the parser must explicitly stop the lexer thread, since otherwise the lexer will just
    fill up the blocking queue and sit and wait for the parser to consume a token.
    */

    var lexerThread : Thread = null
    var parserThread : Thread = null

    parserThread = new Thread(new Runnable {
      override def run() {
        try {
          runParser()
        } catch {
          case e: InterruptedException =>  // do nothing, we've been told to stop
          case e: ParseCancellationException =>  // do nothing, we've been told to stop
        }finally{
          lexerThread.interrupt()
        }
      }
      def runParser() {
        val lexerWrapper = new WrappedLexer(tokens)
        val tokenStream = new UnbufferedTokenStream(lexerWrapper)
        val parser = new generated.SVParser(tokenStream)

        // disable error recovery (causes NPE due to requiring a CharSource in the TokenStream)
        parser.setErrorHandler(new BailErrorStrategy())
        parser.setTrace( debugOptions.contains("trace") )
        parser.setBuildParseTree(true)

        try {
          val tree = parser.source_text()
          logger.info("SUCCESS")
        } catch {
          case e: ParseCancellationException => {
            val cause = e.getCause.asInstanceOf[RecognitionException]
            val tok = cause.getOffendingToken().asInstanceOf[SVToken]
            reportError(cause, tok)

            // TODO FIXME this is bad.
            // Instead return a Future or Promise or something and return this in the main function.
            sys.exit(1)
          }
          case e: CancellationException => {
            logger.info("Parser cancelled.")
          }
        }
      }
      def reportError(e: RecognitionException, tok: SVToken){
        val sb = new StringBuilder
        val toktext = tok.getText()
        sb ++= "Parsing failed. Found: %s".format(toktext)
        if ( tok.isEOF ) {
            sb ++= "\n"
        }else{
          val typetext = LexerTokens.tokenConstText(tok.getType())
          if ( toktext != typetext ) {
            sb ++= "(%s)\n".format(typetext)
          } else {
            sb ++= "\n"
          }
        }
        sb ++= "Expected one of: "
        for ( i <- e.getExpectedTokens.toList ){
          if ( i >= 0 ) {
            sb ++= "%s ".format(LexerTokens.tokenConstText(i))
          }else{
            sb ++= "<eof>"
          }
        }
        sb ++= "\n"
        if ( tok.ctx != null ){
          printContextChain(sb, tok.ctx, tok.line, tok.col)
        }
        logger.error(sb.toString)
      }
    })

    lexerThread = new Thread(new Runnable {
      override def run() {
        try {
          runLexer()
        } catch {
          case e: InterruptedException => parserThread.interrupt()
          case e: LexerError => {
            // NOTE: any lexer error will also produce an error token in the token stream, thus
            // stopping the parser naturally (via failure to match any rules).
            val sb = new StringBuilder
            sb ++= "Lexical error: %s\n".format(e.msg)
            printContextChain(sb, e.ctx, e.line, e.col)
            sb ++= e.ctx.what()
            logger.error(sb.toString)
          }
          case e: Throwable => {
            // make sure the parser is interrupted
            parserThread.interrupt()
            // rethrow the exception
            throw e
          }
        }
      }
      def runLexer() {
        val lexer = new Lexer(tokens, incdirs, printTokens, skipTokenEnqueue)
        lexer.scan(sources, defines)
      }
    })

    if ( debugOptions.contains("wait_stdin") ){
      // useful for profiling (connect to process before continuing)
      readLine()
    }

    lexerThread.start()

    if ( !lexerOnly ){
      parserThread.start()
    }
  }

  def printContextChain(sb:StringBuilder, ctx:Context, line:Int, col:Int){
    sb ++= "In: %s(%d,%d)\n".format(ctx.where(), line, col)
    var parent = ctx.parent
    var child = ctx
    while ( parent != null ){
      sb ++= "Referenced from: %s(%d,%d)\n".format(parent.getFileName(), child.line, child.col)
      child = parent
      parent = parent.parent
    }
  }
}
