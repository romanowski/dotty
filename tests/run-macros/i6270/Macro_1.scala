import scala.quoted._

object api {
  extension (inline x: String) inline def reflect : String =
    ${ reflImpl('x) }

  private def reflImpl(x: Expr[String])(using qctx: QuoteContext) : Expr[String] = {
    import qctx.reflect._
    Expr(x.show)
  }

  extension (x: => String) inline def reflectColor : String =
    ${ reflImplColor('x) }

  private def reflImplColor(x: Expr[String])(using qctx: QuoteContext) : Expr[String] = {
    import qctx.reflect._
    Expr(x.showAnsiColored)
  }
}
