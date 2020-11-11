import scala.quoted._

class Foo {
  def f(using QuoteContext): Expr[Any] = {
    '{
      trait X {
        type Y
        def y: Y = ???
      }
      val x: X = ???
      type Z = x.Y
      ${
        val t: Type[Z] = Type[Z]
        '{ val y: Z = x.y }
        '{ val y: t.Underlying = x.y }
      }
    }
  }
}
