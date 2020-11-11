import scala.quoted._
class Foo {
  def f[T2](t: Type[T2])(using QuoteContext) = t match {
    case '[ *:[Int, t2] ] =>
      Type[ *:[Int, t2] ]
  }
}
