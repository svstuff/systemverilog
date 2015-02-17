// -----------------------------------------------------------------------------------------------
// Lexer for System Verilog 2012
// -----------------------------------------------------------------------------------------------
// Input: path to a project file (XML file describing the source files, include directories, etc).
// Output: stream of tokens, with `include's and `define's fully expanded (but note [1])
//
// Tokens carry the following info (directly or indirectly):
// - the file in which it was encountered
// - the line/col in this file
// - WHY it was scanned (i.e. the current `include/`define chain)
//
// On a lexer/parser error the diagnostic logic can easily print the correct include chain,
// since the required information is available in the token itself (a special error token is
// made for lexer errors).
//
// Types of tokens recognized (intentionally incomplete, the parser should do most of the work):
// - operators, parenthesis and brackets
// - identifiers
// - numbers
// - string literals
//
// Some tokens will be discarded or expanded into multiple other tokens by the lexer:
// - comments (currently discarded, could be produced on a separate channel)
// - `define's (stored internally, grammar need not worry about these)
// - `define invocations (expanded implicitly)
// - `include's (expanded implicitly)
// - `ifdef/`ifndef etc (skipped or expanded implicitly)
//
// This is because these can appear anywhere in the source text, making it very hard to deal
// with them in the parser. Instead of forwarding these directly to the parser the resulting
// tokens will be annotated so the original context can be retrieved.
//
// Some comments are preceeded by [LRM]. These comments are quotes from the SV language reference.
//
// Footnotes:
//
// [1] macro expansion is easier to do as a separate step before the parser, since this greatly
// simplifies the grammar. The same applies to `ifdef's (can appear anywhere in the source text).
//
// However, enough information should be passed with the tokens to enable
// the AST to reflect that a given AST node is actually a result of an invokation of a given macro.
//
// E.g. it should be possible to walk the AST and find all occurrences of
//
// `log_debug("blah")
//
// -----------------------------------------------------------------------------------------------

package com.github.misfornoyd.systemverilog

import io.Source
import java.util.concurrent.BlockingQueue
import java.io.File
import org.antlr.v4.runtime.Token

import generated._

import java.nio.charset.CodingErrorAction
import scala.io.Codec

sealed class Lexer(
  tokens : BlockingQueue[SVToken],
  incdirs : Seq[String],
  printTokens : Boolean,
  skipEnqueue: Boolean) extends com.typesafe.scalalogging.slf4j.Logging {

  implicit val codec = Codec("UTF-8")
  codec.onMalformedInput(CodingErrorAction.REPLACE)
  codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

  // ANTLR requires each token to know its index in the token stream.
  var currentTokenIndex = 0

  // The current file context (used for error reporting).
  var currentContext : Context = null

  // map from macro name to text
  val defines = new Defines()

  val special = "()[]{}.,:;*/%+-=|&^$#@<>!~"
  val whitespaceNoNewline = " \t\f"

  val skipInPrescan = List(
    "__FILE__", "__LINE__", "line",
    "ifdef", "ifndef", "elsif", "else", "endif",
    "timescale", "undef", "undefineall", "define", "resetall", "include", "default_nettype",
    "celldefine", "endcelldefine", "pragma", "begin_keywords", "end_keywords",
    "unconnected_drive", "nounconnected_drive" )

  def scan ( sources:Seq[String], defs:collection.immutable.Map[String, String] ) {
    // add the initial defines (typically provided on the command line)
    for ( (name, value) <- defs ){
      // TODO FIXME cmdline defines can probably be more complex than simple key=value
      defines.defines += name -> new Define(null, 0, 0, name, List(), List(new PTokenText(value)))
    }

    // scan each source file
    for ( f <- sources ) {
      currentContext = new FileContext(null, 0,0, f)
      scanSourceFile(f)
    }
    currentContext = null
    produce( Token.EOF, 0,0)
  }

  def scanSourceFile ( filePath : String ) {
    logger.debug(s"Scanning source file: <${filePath}>")
    val source = new SimpleSource(1, 1, Source.fromFile(filePath).mkString)
    try {
      scanSource( source )
    } finally {
      source.close
    }
    logger.debug(s"Done with source file: <${filePath}>")
  }

  def scanIncludeFile( line : Int, col : Int, filePath : String ) {
    logger.debug(s"Scanning include file: <${filePath}>")

    // attempt to open a source for the include file.
    val (actualFilePath, source) = openIncludeFileSource(line, col, filePath)

    // we succeeded in opening include file, so switch context and scan it.
    val prevContext = currentContext
    currentContext = new FileContext(prevContext, line, col, actualFilePath)

    try {
      scanSource( source )
    } finally {
      source.close
    }

    currentContext = prevContext
    logger.debug(s"Done with include file: <${filePath}>")
  }

  def openIncludeFileSource ( line:Int, col:Int, filePath:String ) : (String, SystemVerilogSource) = {
    // first try to open the unmodified path
    try {
      return (filePath, new SimpleSource(1, 1, Source.fromFile(filePath).mkString))
    }catch{
      case e : java.io.FileNotFoundException => // ignore, will try all the include directories also.
    }

    // this failed, so now try each include directory in turn.
    for ( incdir <- incdirs ){
      val p = "%s/%s".format(incdir, filePath)
      logger.trace(s"Trying path <${p}>")
      try {
        return (p, new SimpleSource(1, 1, Source.fromFile(p).mkString))
      }catch{
        case e : java.io.FileNotFoundException => // ignore, try the next include dir.
      }
    }

    // could not open include file even when trying all include directories, so raise lexer error.
    throw lexerError("could not open file %s".format(filePath), currentContext, line, col)
  }

  def scanDefineInvokation( source : SystemVerilogSource, line : Int, col : Int, d : Define ) {
    // Pre-condition: the macro ID has been scanned, but not the paramlist (if any)
    // [LRM] White space shall be allowed between the text macro name and the left parenthesis in the macro usage.
    source.dropWhile( _.isWhitespace )
    val params = (
      if (d.params.length != 0 && source.hasNext && source.peek == '(')
        getActualParams(source).map( p => new ActualParam(List(new PTokenText(p))) )
      else
        List.empty[ActualParam]
    )
    if ( d.params.length != params.length ) {
      // TODO it is actually allowed to have fewer actual params if all remaining have default values!
      throw lexerError("Incorrect number of actual parameters (actual %d, formal %d)".format(params.length, d.params.length), currentContext, line, col)
    }

    // parameter substitution
    val text = defines.expand(d, params)
    logger.trace(s"Expanded define ${d.id} to: <${text}>")

    // Scan the substituted text in a new context
    val prevContext = currentContext
    currentContext = new MacroContext(prevContext, line, col, d, text)
    val macroSource = new SimpleSource(d.line, d.col, text)
    try {
      scanSource(macroSource)
      currentContext = prevContext
    } finally {
      macroSource.close()
    }
  }

  def getActualParams(source : SystemVerilogSource) : List[String] = {
    // [LRM]
    // Actual arguments and defaults shall not contain comma or
    // right parenthesis characters outside matched pairs of left and right parentheses (), square brackets [],
    // braces {}, double quotes "", or an escaped identifier.
    // NOTE: the spec does not mention whether this also applies to a /* comment */.
    source.drop(1) // drop lparen
    var balance = 1
    val param = new StringBuilder
    val params = collection.mutable.ListBuffer.empty[String]
    while ( balance > 0 ) {
      val c = nextChar( source )
      if ( c == '"' ){
        param += '"'
        param ++= scanStringLiteral(source)
        param += '"'
      } else if ( "({[".contains(c) ){
        balance += 1
        param += c
      } else if ( ")}]".contains(c) ){
        balance -= 1
        if ( balance > 0 ){
          param += c
        }
      } else if ( c == ',' && balance == 1 ){
        // Given `FOO((abc),`BAR(d,e,f)) - we are at "c)," and not "(d,"
        params += param.toString.trim
        param.clear
      } else {
        param += c
      }
    }
    // Handle the final parameter
    params += param.toString.trim
    params.toList
  }

  def getActualParam(id:String, d:Define, params:Seq[String]) : Option[String] = {
    var i = 0
    for ( formal <- d.params ){
      if ( formal.id == id ) return Some(params(i))
      i += 1
    }
    None
  }

  def scanDefine( source : SystemVerilogSource ) {
    /*
    [LRM]
    If formal arguments are used, the list of formal argument names shall be enclosed in parentheses following
    the name of the macro. The formal argument names shall be simple_identifiers, separated by commas and
    optionally white space. The left parenthesis shall follow the text macro name immediately, with no space in
    between.

    [LRM]
    Actual arguments and defaults shall not contain comma or
    right parenthesis characters outside matched pairs of left and right parentheses (), square brackets [],
    braces {}, double quotes "", or an escaped identifier.
    */
    // TODO is this correct? newlines?
    source.dropWhile( _.isWhitespace )
    val id = source.takeWhile( (c) => { c.isLetterOrDigit || c == '_' } )
    logger.trace(s"START DEFINE id: ${id}")

    // Extract formal parameters
    // TODO default values
    val params = collection.mutable.ListBuffer.empty[FormalParam]
    if ( source.peek == '(' ){
      source.drop(1)
      var more = true
      var index = 0
      while ( more ){
        source.dropWhile( _.isWhitespace )
        val paramId = source.takeWhile( (c) => { c.isLetterOrDigit || c == '_' } )
        // TODO check that the param id is unique
        params += new FormalParam(index, paramId, None)
        index += 1
        source.dropWhile( _.isWhitespace )
        val next = nextChar( source )
        if ( next == ')' ){
          more = false
        } else if ( next != ',' ) {
          throw lexerError("expected ',' or ')' but found '%c'".format(next), currentContext, source.line, source.col )
        }
      }
    }

    source.dropWhile( whitespaceNoNewline.contains(_) )
    val startLine = source.line
    val startCol = source.col

    val text = scanDefineText( source )
    logger.trace(s"MACROTEXT id:${id}, text:<${text.toString}>")

    val ptokens = preScanMacro( text, params.toList )
    ptokens.foreach(t => logger.trace(s"PTOKEN: ${t.toString}"))

    defines.defines += id -> new Define(currentContext, startLine, startCol, id, params.toList, ptokens)
  }

  def getParamForId(id: String, formals: Seq[FormalParam]) : Option[FormalParam] = {
    for ( formal <- formals ){
      if ( formal.id == id ) return Some(formal)
    }
    None
  }

  def preScanMacro( text: String, formals: Seq[FormalParam] ) : List[PToken] = {
    // Scan the macro text, producing a list of preprocessor tokens.
    // TODO this method is way too complex for what it needs to do.
    val source = new SimpleSource(0,0,text)
    val sb = new StringBuilder
    var isBoundary = true
    val ptokens = collection.mutable.ListBuffer.empty[PToken]

    while ( source.hasNext ){

      val c = source.peek

      if ( !isBoundary ){
        isBoundary = c.isWhitespace || special.contains(c)
        sb += source.next
      } else {

        if ( c.isLetter || c == '_' ) {
          // start of identifier, check if it matches a formal parameter
          val id = source.takeWhile( (c) => { c.isLetterOrDigit || c == '_' } )
          getParamForId(id, formals) match {
            case Some(param) => {
              // the id is a formal parameter.
              // add the text thus far as a simple pretoken
              if ( !sb.isEmpty ) {
                ptokens += new PTokenText(sb.toString)
                sb.clear
              }
              // add the formal param as a special pretoken for later expansion
              ptokens += new PTokenParam(param)
            }
            case None => {
              // not a formal param, carry on
              sb ++= id
            }
          }
        } else if ( c == '/' ) {
          sb += source.next
          if ( source.peek == '/' ) {
            // TODO: the LRM states that single-line comments cannot be part of macro text.
            sb ++= consumeSingleLineComment(source)
          } else if ( source.peek == '*' ){
            sb += source.next
            sb ++= consumeMultiLineComment(source)
          }
        } else if ( c == '"' ) {
          sb += source.next
          sb ++= scanStringLiteral(source)
          sb += '"'
        } else if ( c == '`' ) {
          // preprocessor usage, drop the backtick
          source.drop(1)
          val c2 = source.peek
          if ( c2 == '`' ){
            // token delimiter, drop the backtick and carry on.
            source.drop(1)
          } else if ( c2 == '"' ) {
            // escaped string literal, so we need to scan inside also. Consume quote and carry on.
            sb += source.next
          } else if ( c2 == '\\' ) {
            // TODO what is this?
            sb += source.next
            val accent = source.next
            val quote = source.next
            if ( accent != '`' || quote != '"' ) {
              throw lexerError("incorrect escape sequence in macro text", currentContext, 0, 0)
            }
            sb += '"'
          } else if ( c2.isLetter || c2 == '_' ) {
            // macro call or compiler directive, extract the id to find which
            val (idRaw, idList) = macroCallId(source, formals)
            if ( idList.length == 1 && skipInPrescan.contains(idRaw) ){
              // skip directives, and treat builtin macros as text (will be handled in normal scanner)
              sb += '`'
              sb ++= idRaw
            }else{
              // macro call, so add the text token and then extract the call token
              if ( !sb.isEmpty ) {
                ptokens += new PTokenText(sb.toString)
                sb.clear
              }
              ptokens ++= preScanMacroCall(source, idList, formals)
            }
          }else{
            throw lexerError("incorrect preprocessor directive", currentContext, 0, 0)
          }
        } else {
          if ( !(c.isWhitespace || special.contains(c)) ){
            // we _were_ on a token boundary, but not anymore
            isBoundary = false
          }
          sb += source.next
        }

      }
    }

    // Add the remainder as a text pretoken
    if ( !sb.isEmpty ) {
      ptokens += new PTokenText(sb.toString)
    }

    ptokens.toList
  }

  /** Returns the macro ID as a String and as a list of preprocessor tokens.
    *
    * The macro ID String is only valid if the list of preprocessor tokens has a single element.
    * Otherwise the real ID must be evaluated from the ptokens within the context of the outer
    * macro (which will have a list of actual parameters).
    *
    * @param source current SV source stream from which to consume characters
    * @param formals sequence of formal parameters for the macro which contains this call
    * @return tuple (raw ID, ptokens) describing the ID of the macro which is being called.
    */
  def macroCallId( source : SystemVerilogSource, formals : Seq[FormalParam] ) : (String, Seq[PToken]) = {
    val idPart = new StringBuilder
    val idList = collection.mutable.ListBuffer.empty[String]
    var more = true

    while ( more && source.hasNext ){
      val c = source.peek
      if ( c.isLetterOrDigit || c == '_' ){
        idPart += source.next
      } else if ( c == '`' ) {
        if ( source.peekn(2) == "``" ){
          source.drop(2)
          idList += idPart.toString
          idPart.clear
        }else{
          more = false
        }
      } else {
        more = false
      }
    }

    if ( !idPart.isEmpty ){
      idList += idPart.toString
    }

    val ptokens = idList.map( a => {
      getParamForId(a, formals) match {
        case Some(param) =>  new PTokenParam(param)
        case None =>         new PTokenText(a)
      }
    })

    (idPart.toString, ptokens)
  }

  def preScanMacroCall( source : SystemVerilogSource, id: Seq[PToken], formals : Seq[FormalParam] ) : List[PToken] = {
    // [LRM] White space shall be allowed between the text macro name and the left parenthesis in the macro usage.
    val ws = source.takeWhile( _.isWhitespace )
    // extract the actual parameters, if any
    if ( source.hasNext && source.peek == '('){
      val actuals = getActualParams(source)
      List(new PTokenCall(id, actuals.map( a => actualParamFromString(a, formals) )))
    } else {
      // no parameters, add back the consumed whitespace
      List(new PTokenCall(id, List.empty[ActualParam]), new PTokenText(ws))
    }
  }

  def actualParamFromString( actual : String, formals : Seq[FormalParam] ) : ActualParam = {
    new ActualParam( preScanMacro( actual, formals ) )
  }

  def scanDefineText( source : SystemVerilogSource ) : String = {
    val text = new StringBuilder
    var escaped = false
    var more = true
    while ( more && source.hasNext ){
      val c = nextChar( source )
      if ( c == '\\' ){
        escaped = !escaped
      } else {
        if ( c == '\n' ) {
          if ( escaped ){
            text += c
          } else {
            more = false
          }
        } else {
          if ( escaped ){
            text += '\\'
          }
          text += c
        }
        escaped = false
      }
    }
    text.toString
  }

  def scanStringLiteral ( source : SystemVerilogSource ) : String = {
    // NOTE: we want to return the string exactly as it appears. We don't want to handle
    // escape characters here (by inserting the character code in place of the escape sequence).
    // TODO we should probably still check that escape sequences are valid.
    var escaped = false
    var more = true
    var text = new StringBuilder
    while ( more && source.hasNext ){
      val c = nextChar( source )
      if ( c == '"' && !escaped ){
        more = false
      } else {
        // add the char, whatever it is (since we are not done with the literal)
        text += c
        // check if the next character should be escaped (we only care about the quote though)
        if ( c == '\\' ){
          // a backslash means the next char is escaped, unless the previous char was also a backslash.
          escaped = !escaped
        }else{
          // a non-backslash char means we definitely exit escape mode
          escaped = false
        }
      }
    }
    text.toString
  }

  def produceNumberLiteral(line:Int, col:Int, lit:String) {
    // TODO use a regex to validate the number against the grammar
    lit.takeRight(2) match {
      case "s" => produce( LexerTokens.LIT_TIME, line, col, lit )
      case "ms" => produce( LexerTokens.LIT_TIME, line, col, lit )
      case "us" => produce( LexerTokens.LIT_TIME, line, col, lit )
      case "ns" => produce( LexerTokens.LIT_TIME, line, col, lit )
      case "ps" => produce( LexerTokens.LIT_TIME, line, col, lit )
      case "fs" => produce( LexerTokens.LIT_TIME, line, col, lit )
      case _ => produce( LexerTokens.LIT_NUM, line, col, lit )
    }
  }

  def scanSource ( source : SystemVerilogSource ) {
    while ( true ) {
      source.dropWhile( _.isWhitespace )
      if ( !source.hasNext ) return

      val line = source.line
      val col = source.col
      val c = source.peek

      if ( c == '`' ) {
        source.drop(1) // drop the backtick
        val directive = source.takeWhile( (c) => { c.isLetterOrDigit || c == '_' } )
        if ( directive == "include" ) {
          source.dropWhile( _ != '\"' )
          source.drop(1)
          val incfile = source.takeWhile( _ != '\"' )
          source.drop(1)
          scanIncludeFile( line, col, incfile )
        } else if ( directive == "define" ) {
          scanDefine( source )
        } else if ( directive == "elsif" || directive == "else" ) {
          // this must be the end of a recursively handled `ifdef/`ifndef, so skip past the rest.
          consumeUntilEndif(source)
          return
        } else if ( directive == "endif") {
          // this must be the end of a recursively handled `ifdef/`ifndef, but there's nothing left to do
          return
        } else if ( directive == "ifdef" || directive == "ifndef" ) {
          source.dropWhile( _.isWhitespace )
          val id = source.takeWhile( !_.isWhitespace )
          val resultToTakeBranch = (directive == "ifdef")
          if ( defines.defines.contains(id) == resultToTakeBranch ) {
            logger.trace(s"${directive} taking path for: ${id}")
            // Recursively handle this whole conditional block (including skipping past `elsif and `endif)
            scanSource(source)
          } else {
            logger.trace(s"${directive} skipping path for ${id} on line ${source.line} of context ${currentContext.fileName}")
            // mutually recursive call to scan the `elsif or `else block
            scanConditionalBlock(source)
          }
        } else if ( directive == "undef" ) {
          source.dropWhile( _.isWhitespace )
          val id = source.takeWhile( !_.isWhitespace )
          defines.defines.remove(id)
          logger.trace(s"Undefining: ${id}")
        } else if ( directive == "undefineall" ) {
          defines.defines.clear()
          logger.trace(s"Undefineall")
        } else if ( directive == "timescale") {
          // TODO should support time literals (rather than just consuming the whole line)
          source.dropWhile( _.isWhitespace )
          produce( LexerTokens.TIMESCALE, line, col, source.takeWhile( _ != '\n' ) )
        } else if ( directive == "__FILE__") {
          produce( LexerTokens.LIT_STRING, line, col, currentContext.fileName )
        } else if ( directive == "__LINE__") {
          produce( LexerTokens.LIT_NUM, line, col, "%s".format(line) )
        } else if ( defines.defines.contains(directive) ){
          scanDefineInvokation( source, line, col, defines.defines(directive) )
        }else{
          throw lexerError("unknown compiler directive: %s".format(directive), currentContext, line, col )
        }
      } else if ( c == ''' ) {
        // potentially a number literal (unbased_unsized_literal or integral number without the size specifier)
        source.drop(1)
        if ( source.hasNext && "01xXzZ".contains(source.peek) ){
          produce( LexerTokens.LIT_UNBASED_UNSIZED, line, col, source.take(1) )
        }else if ( source.hasNext && ("sS".contains(source.peek) || "bBdDoOhH".contains(source.peek) ) ){
          // this must be a number literal, so consume until end
          val num = source.takeWhile( c => { c.isLetterOrDigit || c == '_' } )
          // TODO use a regex to validate the number against the grammar
          produce( LexerTokens.LIT_NUM, line, col, ''' + num )
        } else {
          // not a number literal, so just produce a single apostrophe
          produce( LexerTokens.APOSTROPHE, line, col )
        }
      } else if ( c.isDigit ) {
        // 1step or a number literal (real or integral)
        val num = source.takeWhile( c => { c.isLetterOrDigit || "_'.".contains(c) } )

        if ( num == "1step" ){
          produce( LexerTokens.KW_1STEP, line, col )
        }else{
          if ( source.hasNext && "+-".contains(source.peek) && "eE".contains(num.takeRight(1)) ){
            // this is a real with exponent followed by sign, so consume the rest also
            val real = num + source.next + source.takeWhile( _.isDigit || c == '_' )
            produceNumberLiteral(line, col, real)
          }else if ( num.takeRight(1) == "'" ){
            // this must be a casting expression (e.g. 64'(blah)).
            produceNumberLiteral(line, col, num.dropRight(1))
            produce( LexerTokens.APOSTROPHE, line, col + num.size - 1 )
          }else{
            produceNumberLiteral(line, col, num)
          }
        }
      } else if ( c == '$' ) {
        source.drop(1)
        val toktext = source.takeWhile( c => { c.isLetterOrDigit || "_$".contains(c) } )
        toktext match {
          case "unit" => produce( LexerTokens.DOLLAR_UNIT, line, col )
          case "root" => produce( LexerTokens.DOLLAR_ROOT, line, col )
          case "fatal" => produce( LexerTokens.DOLLAR_FATAL, line, col )
          case "error" => produce( LexerTokens.DOLLAR_ERROR, line, col )
          case "warning" => produce( LexerTokens.DOLLAR_WARNING, line, col )
          case "info" => produce( LexerTokens.DOLLAR_INFO, line, col )
          case "setup" => produce( LexerTokens.DOLLAR_SETUP, line, col )
          case "hold" => produce( LexerTokens.DOLLAR_HOLD, line, col )
          case "setuphold" => produce( LexerTokens.DOLLAR_SETUPHOLD, line, col )
          case "recovery" => produce( LexerTokens.DOLLAR_RECOVERY, line, col )
          case "removal" => produce( LexerTokens.DOLLAR_REMOVAL, line, col )
          case "recrem" => produce( LexerTokens.DOLLAR_RECREM, line, col )
          case "skew" => produce( LexerTokens.DOLLAR_SKEW, line, col )
          case "timeskew" => produce( LexerTokens.DOLLAR_TIMESKEW, line, col )
          case "fullskew" => produce( LexerTokens.DOLLAR_FULLSKEW, line, col )
          case "period" => produce( LexerTokens.DOLLAR_PERIOD, line, col )
          case "width" => produce( LexerTokens.DOLLAR_WIDTH, line, col )
          case "nochange" => produce( LexerTokens.DOLLAR_NOCHANGE, line, col )
          case _ => produce( LexerTokens.SYSTEM_ID, line, col, toktext )
        }
      } else if ( c.isLetter || c == '_' ) {
        val toktext = source.takeWhile( c => { c.isLetterOrDigit || c == '_' || c == '$' } )
        LexerTokens.keywords.get(toktext) match {
          case Some(toktype) => produce( toktype, line, col )
          case None => produce( LexerTokens.ID, line, col, toktext )
        }
      } else if ( c == '"' ) {
        source.drop(1)
        val toktext = scanStringLiteral( source )
        // TODO make a map of special string literals, similar to the keyword handling.
        // Special string literals are those that appear in the grammar in the LRM.
        if ( toktext.startsWith("DPI") ){
          if ( toktext == "DPI-C" ){
            produce( LexerTokens.LIT_STRING_DPI_C, line, col, toktext )
          } else if ( toktext == "DPI" ) {
            produce( LexerTokens.LIT_STRING_DPI, line, col, toktext )
          } else {
            produce( LexerTokens.LIT_STRING, line, col, toktext )
          }
        } else {
          produce( LexerTokens.LIT_STRING, line, col, toktext )
        }
      } else if ( c == '/' ) {
        // check for comment
        source.drop(1)
        source.peek match {
          case '/' => { source.drop(1); consumeSingleLineComment(source); }
          case '*' => { source.drop(1); consumeMultiLineComment(source); }
          case _ => produce( LexerTokens.DIV, line, col )
        }
      } else if ( c == ':' ) {
        // Need to special-case for operators that could end with a slash. Afaict this is only :/.
        // check for :/, which could either be an operator or a colon followed by a comment.
        val wat = source.peekn(3)
        if ( wat.length != 3 ){
          // didn't get 3 characters, hence this must be :/ or some other operator.
          produceOperator( source, line, col )
        }else if( wat(1) != '/' ){
          // ok, we have 3 characters, but it's not :/
          produceOperator( source, line, col )
        }else{
          // this could be either ":/" or ":// comment" or ":/* multi-comment".
          if ( "*/".contains(wat(2)) ){
            // assume this means COLON then a comment.
            source.drop(1)
            produce( LexerTokens.COLON, line, col )
          }else{
            // must be :/ followed by something else.
            source.drop(2)
            produce( LexerTokens.COLON_DIV, line, col )
          }
        }
      } else {
        produceOperator( source, line, col )
      }
    }
  }

  def produceOperator( source : SystemVerilogSource, line : Int, col : Int ) {
    // The character must be one of the special characters (or not a valid token at all).
    // Try to get the longest sequence of characters that corresponds to an operator.
    val candidate = source.peekn(LexerTokens.operatorMaxLength)
    candidate match {
      case LexerTokens.operatorPattern(operator) => {
        source.drop(operator.length)
        produce(LexerTokens.operators(operator), line, col)
      }
      case _ => throw lexerError("\"%c\" is not legal".format(candidate(0)), currentContext, line, col )
    }
  }

  def scanConditionalBlock ( source : SystemVerilogSource ) {
    while( source.hasNext ){
      val cond = consumeUntilNextConditional( source )
      if ( cond == "endif" ) {
        return
      }
      if ( cond == "else" ) {
        scanSource(source)
        // TODO match `endif here!
        return
      }
      // TODO assert cond == `elsif
      source.dropWhile( _.isWhitespace )
      val id = source.takeWhile( !_.isWhitespace )
      if ( defines.defines.contains(id) ) {
        scanSource(source)
        return
      }
      // else : go another round looking for the next `elsif block (or `endif)
    }
  }

  def consumeUntilNextConditional ( source : SystemVerilogSource ) : String = {
    consumeUntilCondBlock( source, false )
  }

  def consumeUntilEndif ( source : SystemVerilogSource ) : String = {
    consumeUntilCondBlock( source, true )
  }

  def consumeUntilCondBlock ( source : SystemVerilogSource, tillTheEnd: Boolean ) : String = {
    // Note, the scan must be sensitive to comments and string literals.
    // I.e. we need to ignore `endif etc that occur inside comments and string literals.
    val start_line = source.line()
    var nesting = 1
    while ( source.hasNext ) {
      source.dropWhile( _.isWhitespace )
      val c = source.next

      if ( c == '"') {
        scanStringLiteral ( source )
      } else if ( c == '/' ) {
        if ( source.peek == '/' ) {
          consumeSingleLineComment(source)
        }else if ( source.peek == '*' ) {
          source.next
          consumeMultiLineComment(source)
        }
      } else if ( c == '`' ) {
        val word = source.takeWhile( c => { !(c.isWhitespace || special.contains(c)) } )
        if ( word == "ifdef" || word == "ifndef" ) {
          nesting += 1
          logger.trace(s"entering nested cond block at line=${source.line}, nesting=${nesting}")
        } else if ( word == "endif" ) {
          nesting -= 1
          logger.trace(s"exiting nested cond block at line=${source.line}, nesting=${nesting}")
          if ( nesting == 0 ){
            // return regardless, since we've reached the end of the conditional block
            return word
          }
        } else if ( nesting == 1 && !tillTheEnd ) {
          // at nesting level 1 we look for `elsif and `else (unless we only care about `endif)
          if ( word == "elsif" || word == "else" ){
            return word
          }
        }
      }
    }
    throw lexerError("mismatched conditional block", currentContext, start_line, 0)
  }

  def consumeSingleLineComment ( source : SystemVerilogSource ) : String = {
    val sb = new StringBuilder
    while ( source.hasNext && source.peek != '\n' ){
      sb += source.next
    }
    sb.toString
  }

  def consumeMultiLineComment ( source : SystemVerilogSource ) : String = {
    val sb = new StringBuilder
    val line = source.line
    val col = source.col
    while ( source.hasNext ){
      val c = source.next
      if ( c == '*' ){
        if ( source.hasNext && source.peek == '/' ){
          source.next
          sb ++= "*/"
          return sb.toString
        }
      }
      sb += c
    }
    throw lexerError("unterminated multi-line comment", currentContext, line, col )
  }

  def lexerError ( msg : String, ctx : Context, line : Int, col : Int ) : LexerError = {
    val token = new SVTokenConst(currentTokenIndex, LexerTokens.ERROR, currentContext, line, col )
    tokens.put(token)
    currentTokenIndex += 1
    new LexerError(msg, currentContext, line, col)
  }

  def produce ( ttype : Int, line : Int, col : Int ) : SVToken = {
    val token = new SVTokenConst(currentTokenIndex, ttype, currentContext, line, col )
    if ( printTokens ){
      logger.debug(s"DEBUG_TOKEN: ${token}")
    }
    if ( !skipEnqueue ){
      tokens.put(token)
    }
    currentTokenIndex += 1
    token
  }

  def produce ( ttype : Int, line : Int, col : Int, text : String ) : SVToken = {
    val token = new SVTokenText(currentTokenIndex, ttype, currentContext, line, col, text)
    if ( printTokens ){
      logger.debug(s"DEBUG_TOKEN: ${token}")
    }
    if ( !skipEnqueue ){
      tokens.put(token)
    }
    currentTokenIndex += 1
    token
  }

  def matchChar( source : SystemVerilogSource, expected : Char ){
    if ( source.hasNext ){
      val c = source.next
      if ( c != expected ){
        throw lexerError("Expected '%c' but found '%c'".format(expected, c), currentContext, source.line, source.col )
      }
    } else {
        throw lexerError("Expected '%c' but found EOF".format(expected), currentContext, source.line, source.col )
    }
  }

  def nextChar ( source : SystemVerilogSource) : Char = {
    try {
      source.next
    } catch {
      case eoferr : java.util.NoSuchElementException =>
        throw lexerError("unexpected EOF", currentContext, source.line, source.col )
    }
  }
}

sealed trait SystemVerilogSource {

  def line () : Int
  def col () : Int
  def close ()
  def next () : Char
  def peek () : Char
  def peekn (n: Int) : String
  def hasNext () : Boolean
  def takeWhile(whilefun: Char => Boolean) : String
  def dropWhile(whilefun: Char => Boolean)
  def take(n : Int) : String
  def drop(n : Int)

}

sealed class SimpleSource(startLine : Int, startCol : Int, text : String) extends SystemVerilogSource {

  // Simple source implementation using the full source text.

  private var index = 0
  private var _line = startLine
  private var _col = startCol

  def line () : Int = _line
  def col () : Int = _col

  def close () {
    // noop
  }
  def next () : Char = {
    val c = text(index)
    index += 1
    if ( c == '\n' ) {
      _line += 1
      _col = 1
    } else {
      _col += 1
    }
    c
  }
  def peek () : Char = {
    text(index)
  }
  def hasNext () : Boolean = {
    index < text.length
  }
  def peekn (n: Int) : String = {
    text.slice(index, index + n)
  }
  def takeWhile(whilefun: Char => Boolean) : String = {
    val s = new StringBuilder
    while ( hasNext && whilefun(peek) ) {
      s += next
    }
    s.toString
  }
  def dropWhile(whilefun: Char => Boolean) {
    while ( hasNext && whilefun(peek) ) {
      next
    }
  }
  def take(n : Int) : String = {
    val s = new StringBuilder
    while ( s.length < n && hasNext ) {
      s += next
    }
    s.toString
  }
  def drop(n : Int) {
    var i = n
    while ( i != 0 && hasNext ) {
      i -= 1
      next
    }
  }
}

sealed abstract class Context {
  val parent : Context
  val line : Int  // position in _parent_
  val col : Int   // position in _parent_

  def shortId() : String
  def fileName() : String
  def where() : String
  def what() : String
}

case class MacroContext ( parent : Context, line : Int, col : Int, d: Define, expanded: String ) extends Context {
  override def toString() : String = d.id
  def shortId() : String = d.id
  def fileName() : String = parent.fileName
  def where() : String = "Macro " + d.id
  def what() : String = {
    s"Macro ${d.id} is defined in ${d.ctx.fileName}:${d.line} and expanded to:\n" + expanded + "\n"
  }
}

case class FileContext ( parent : Context, line : Int, col : Int, id : String ) extends Context {
  override def toString() : String = fileName
  override def shortId() : String = new File(id).getName()
  def fileName() : String = id
  def where() : String = fileName
  def what() : String = ""
}

sealed class LexerError(val msg: String, val ctx: Context, val line: Int, val col: Int) extends RuntimeException(msg) {
  override def toString() : String = {
    "%s - ctx:%s, line:%d, col:%d".format(msg, ctx, line, col)
  }
}
