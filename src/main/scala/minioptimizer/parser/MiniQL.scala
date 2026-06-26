package minioptimizer.parser

import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}

import minioptimizer.parser.generated.{MiniQLLexer, MiniQLParser}
import minioptimizer.ast.SelectStatement


object MiniQL:

  def parse(sql: String): SelectStatement =
    val lexer = new MiniQLLexer(CharStreams.fromString(sql))
    lexer.removeErrorListeners()
    lexer.addErrorListener(ThrowingErrorListener)

    val parser = new MiniQLParser(new CommonTokenStream(lexer))
    parser.removeErrorListeners()
    parser.addErrorListener(ThrowingErrorListener)

    AstBuilder.buildQuery(parser.statement().query())
