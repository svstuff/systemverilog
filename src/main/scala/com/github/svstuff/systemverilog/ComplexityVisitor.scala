package com.github.svstuff.systemverilog

import java.io._
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._
import com.github.svstuff.systemverilog.generated._

/**
  * Write complexity metrics as a YAML sequence.
  * TODO: include some useful metrics, like cyclomatic complexity.
  */
class ComplexityVisitor(parser: Parser, out: Writer) extends SVVisitor {

  sealed class StatementScope(val name: String, val typ: Char) {
    var nStatements = 0
    var maxLevel = 0
  }

  val scope = new collection.mutable.Stack[StatementScope]
  val logger = org.slf4j.LoggerFactory.getLogger("ComplexityVisitor")
  val sb = new StringBuilder
  var currentLevel = 0

  override def start(){
    // No-op
  }

  override def finish(){
    out.close
    logger.info("Complexity analysis written to file.")
  }

  override def visitInterface_declaration(ctx: SVParser.Interface_declarationContext) {
    scope.push(new StatementScope(ctx.interface_identifier(0).getStart.getText, 'i'))
    super.visitInterface_declaration(ctx)
    scope.pop
  }

  override def visitClass_declaration(ctx: SVParser.Class_declarationContext) {
    scope.push(new StatementScope(ctx.class_identifier(0).getStart.getText, 'c'))
    super.visitClass_declaration(ctx)
    scope.pop
  }

  override def visitPackage_declaration(ctx: SVParser.Package_declarationContext) {
    scope.push(new StatementScope(ctx.package_identifier(0).getStart.getText, 'p'))
    super.visitPackage_declaration(ctx)
    scope.pop
  }

  override def visitFunction_declaration(ctx: SVParser.Function_declarationContext) {
    val start = ctx.getStart.asInstanceOf[SVToken]
    val len = ctx.getStop.getLine - start.getLine
    val name = ctx.function_identifier(0).getStart.getText
    val qualified = scope.foldLeft("") { (a,i) => a + i.name }

    scope.push(new StatementScope(name, 'f'))
    super.visitFunction_declaration(ctx)
    val s = scope.pop

    sb ++= "- {"
    sb ++= "type: f, "
    sb ++= s"name: '$qualified::$name', "
    sb ++= s"file: '${start.ctx.fileName}', "
    sb ++= s"line: ${start.getLine}, "
    sb ++= s"length: $len, "
    sb ++= s"max_level: ${s.maxLevel}, "
    sb ++= s"n_statements: ${s.nStatements} }\n"

    out.write( sb.toString )
    sb.clear
  }

  override def visitTask_declaration(ctx: SVParser.Task_declarationContext) {
    val start = ctx.getStart.asInstanceOf[SVToken]
    val len = ctx.getStop.getLine - start.getLine
    val name = ctx.task_identifier(0).getStart.getText
    val qualified = scope.foldLeft("") { (a,i) => a + i.name }

    scope.push(new StatementScope(name, 't'))
    super.visitTask_declaration(ctx)
    val s = scope.pop

    sb ++= "- {"
    sb ++= "type: t, "
    sb ++= s"name: '$qualified::$name', "
    sb ++= s"file: '${start.ctx.fileName}', "
    sb ++= s"line: ${start.getLine}, "
    sb ++= s"length: $len, "
    sb ++= s"max_level: ${s.maxLevel}, "
    sb ++= s"n_statements: ${s.nStatements} }\n"

    out.write( sb.toString )
    sb.clear
  }

  override def visitSeq_block(ctx: SVParser.Seq_blockContext) {
    // seq_block is a child of statement_item, which means we incremented the
    // current level right before we got here. This means we would see BAR as
    // being 2 levels below FOO in the following:
    //
    //   FOO; if ( blah ) begin BAR; end
    //
    // To correct for this we simply decrease the level here and treat the
    // following statements as children of the if-statement rather than the
    // seq_block. This also means that a freestanding seq_block does not
    // increase the level, which is arguably a sensible approach anyway (for
    // some definition of "level").
    //
    // Simply saying "if(a) begin b" is 3 levels whereas "if(a) b" is 2 seems
    // unhelpful given that they both contribute the same to the perceived
    // complexity of the surrounding code. (If anything we should penalize the
    // latter).
    currentLevel -= 1
    super.visitSeq_block(ctx)
    currentLevel += 1
  }

  override def visitStatement_item(ctx: SVParser.Statement_itemContext) {
    currentLevel += 1
    if ( scope.nonEmpty ){
      scope.top.nStatements += 1
      scope.top.maxLevel = math.max(scope.top.maxLevel, currentLevel)
    }
    super.visitStatement_item(ctx)
    currentLevel -= 1
  }

}
