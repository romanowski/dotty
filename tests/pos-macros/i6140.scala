import scala.quoted._
sealed trait Trait[T] {
  type t = T
}

object O {
  def fn[T:Type](t : Trait[T])(using QuoteContext): Type[T] = Type[t.t]
}
