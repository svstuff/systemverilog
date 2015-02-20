package com.github.misfornoyd.systemverilog

import org.antlr.v4.runtime._
import org.antlr.v4.runtime.tree._

class NullSerializerVisitor extends SerializerVisitor {

  override def visitChildren(ruleNode: RuleNode){
    // do nothing
  }

}
