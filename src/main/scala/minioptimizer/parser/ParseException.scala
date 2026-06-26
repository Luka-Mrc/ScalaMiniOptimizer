package minioptimizer.parser

import org.antlr.v4.runtime.{BaseErrorListener, RecognitionException, Recognizer}

/** Thrown when MiniQL text is syntactically invalid. */
class ParseException(message: String) extends RuntimeException(message)

/** ANTLR error listener that fails fast by throwing instead of printing to stderr. */
object ThrowingErrorListener extends BaseErrorListener:
  override def syntaxError(
      recognizer: Recognizer[?, ?],
      offendingSymbol: Any,
      line: Int,
      charPositionInLine: Int,
      msg: String,
      e: RecognitionException
  ): Unit =
    throw new ParseException(s"Sintaksna greska na liniji $line:$charPositionInLine — $msg")
