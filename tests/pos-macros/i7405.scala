import scala.quoted._
class Foo {
  def f(using QuoteContext): Expr[Any] = {
    '{
      type X = Int // Level 1
      val x: X = ???
      ${
        val t: Type[X] = Type[X] // Level 0
        '{ val y: t.Underlying = x }
      }
    }
  }
}
