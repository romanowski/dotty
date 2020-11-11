import scala.quoted._

object scalatest {
  inline def assert2(condition: => Boolean): Unit =
    ${ assertImpl('condition, Expr("")) } // error

  def assertImpl(condition: Expr[Boolean], clue: Expr[Any])(using QuoteContext): Expr[Unit] =
    '{}
}
