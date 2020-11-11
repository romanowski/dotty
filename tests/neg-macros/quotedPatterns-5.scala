import scala.quoted._
object Test {
  def test(x: quoted.Expr[Int])(using QuoteContext): Unit = x match {
    case '{ type t; 4 } => Type[t]
    case '{ type t; poly[t]($x); 4 } => // error: duplicate pattern variable: t
    case '{ type `t`; poly[`t`]($x); 4 } =>
      Type[t] // error
    case _ =>
  }

  def poly[T](x: T): Unit = ()

}
