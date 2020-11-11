import scala.quoted._

object Macro {

  trait AddInt[A <: Int, B <: Int] { type Out <: Int }

  transparent inline def ff[A <: Int, B <: Int](): AddInt[A, B] = ${ impl[A, B] }

  def impl[A <: Int : Type, B <: Int : Type](using qctx: QuoteContext) : Expr[AddInt[A, B]] = {
    import qctx.reflect._

    val ConstantType(Constant.Int(v1)) = TypeRepr.of[A]
    val ConstantType(Constant.Int(v2)) = TypeRepr.of[B]

    Literal(Constant.Int(v1 + v2)).tpe.asType match
      case '[t] => '{ null: AddInt[A, B] { type Out = t } }
  }
}
