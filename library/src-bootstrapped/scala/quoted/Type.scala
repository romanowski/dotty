package scala.quoted

import scala.annotation.compileTimeOnly

/** Quoted type (or kind) `T` */
abstract class Type[T <: AnyKind] private[scala]:
  /** The type represented `Type` */
  type Underlying = T
end Type

/** Some basic type tags, currently incomplete */
object Type:

  /** Show a source code like representation of this type without syntax highlight */
  def show[T](using tp: Type[T])(using qctx: QuoteContext): String =
    qctx.reflect.TypeTree.of[T].show

  /** Shows the tree as fully typed source code colored with ANSI */
  def showAnsiColored[T](using tp: Type[T])(using qctx: QuoteContext): String =
    qctx.reflect.TypeTree.of[T].showAnsiColored

  /** Return a quoted.Type with the given type */
  @compileTimeOnly("Reference to `scala.quoted.Type.apply` was not handled by PickleQuotes")
  given apply[T <: AnyKind] as (QuoteContext ?=> Type[T]) = ???

end Type
