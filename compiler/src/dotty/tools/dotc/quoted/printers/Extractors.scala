package dotty.tools.dotc.quoted.printers

import scala.quoted._

object Extractors {

  def showTree(using QuoteContext)(tree: qctx.reflect.Tree): String =
    new ExtractorsPrinter[qctx.type]().visitTree(tree).result()

  def showType(using QuoteContext)(tpe: qctx.reflect.TypeRepr): String =
    new ExtractorsPrinter[qctx.type]().visitType(tpe).result()

  def showConstant(using QuoteContext)(const: qctx.reflect.Constant): String =
    new ExtractorsPrinter[qctx.type]().visitConstant(const).result()

  def showSymbol(using QuoteContext)(symbol: qctx.reflect.Symbol): String =
    new ExtractorsPrinter[qctx.type]().visitSymbol(symbol).result()

  def showFlags(using QuoteContext)(flags: qctx.reflect.Flags): String = {
    import qctx.reflect._
    val flagList = List.newBuilder[String]
    if (flags.is(Flags.Abstract)) flagList += "Flags.Abstract"
    if (flags.is(Flags.Artifact)) flagList += "Flags.Artifact"
    if (flags.is(Flags.Case)) flagList += "Flags.Case"
    if (flags.is(Flags.CaseAccessor)) flagList += "Flags.CaseAccessor"
    if (flags.is(Flags.Contravariant)) flagList += "Flags.Contravariant"
    if (flags.is(Flags.Covariant)) flagList += "Flags.Covariant"
    if (flags.is(Flags.Enum)) flagList += "Flags.Enum"
    if (flags.is(Flags.Erased)) flagList += "Flags.Erased"
    if (flags.is(Flags.ExtensionMethod)) flagList += "Flags.ExtensionMethod"
    if (flags.is(Flags.FieldAccessor)) flagList += "Flags.FieldAccessor"
    if (flags.is(Flags.Final)) flagList += "Flags.Final"
    if (flags.is(Flags.HasDefault)) flagList += "Flags.HasDefault"
    if (flags.is(Flags.Implicit)) flagList += "Flags.Implicit"
    if (flags.is(Flags.Inline)) flagList += "Flags.Inline"
    if (flags.is(Flags.JavaDefined)) flagList += "Flags.JavaDefined"
    if (flags.is(Flags.Lazy)) flagList += "Flags.Lazy"
    if (flags.is(Flags.Local)) flagList += "Flags.Local"
    if (flags.is(Flags.Macro)) flagList += "Flags.Macro"
    if (flags.is(Flags.ModuleClass)) flagList += "Flags.ModuleClass"
    if (flags.is(Flags.Mutable)) flagList += "Flags.Mutable"
    if (flags.is(Flags.Object)) flagList += "Flags.Object"
    if (flags.is(Flags.Override)) flagList += "Flags.Override"
    if (flags.is(Flags.Package)) flagList += "Flags.Package"
    if (flags.is(Flags.Param)) flagList += "Flags.Param"
    if (flags.is(Flags.ParamAccessor)) flagList += "Flags.ParamAccessor"
    if (flags.is(Flags.Private)) flagList += "Flags.Private"
    if (flags.is(Flags.PrivateLocal)) flagList += "Flags.PrivateLocal"
    if (flags.is(Flags.Protected)) flagList += "Flags.Protected"
    if (flags.is(Flags.Scala2x)) flagList += "Flags.Scala2x"
    if (flags.is(Flags.Sealed)) flagList += "Flags.Sealed"
    if (flags.is(Flags.StableRealizable)) flagList += "Flags.StableRealizable"
    if (flags.is(Flags.Static)) flagList += "Flags.javaStatic"
    if (flags.is(Flags.Synthetic)) flagList += "Flags.Synthetic"
    if (flags.is(Flags.Trait)) flagList += "Flags.Trait"
    flagList.result().mkString(" | ")
  }

  private class ExtractorsPrinter[QCtx <: QuoteContext & Singleton](using val qctx: QCtx) { self =>
    import qctx.reflect._

    private val sb: StringBuilder = new StringBuilder

    def result(): String = sb.result()

    def visitTree(x: Tree): this.type = x match {
      case Ident(name) =>
        this += "Ident(\"" += name += "\")"
      case Select(qualifier, name) =>
        this += "Select(" += qualifier += ", \"" += name += "\")"
      case This(qual) =>
        this += "This(" += qual += ")"
      case Super(qual, mix) =>
        this += "Super(" += qual += ", " += mix += ")"
      case Apply(fun, args) =>
        this += "Apply(" += fun += ", " ++= args += ")"
      case TypeApply(fun, args) =>
        this += "TypeApply(" += fun += ", " ++= args += ")"
      case Literal(const) =>
        this += "Literal(" += const += ")"
      case New(tpt) =>
        this += "New(" += tpt += ")"
      case Typed(expr, tpt) =>
        this += "Typed(" += expr += ", "  += tpt += ")"
      case NamedArg(name, arg) =>
        this += "NamedArg(\"" += name += "\", " += arg += ")"
      case Assign(lhs, rhs) =>
        this += "Assign(" += lhs += ", " += rhs += ")"
      case Block(stats, expr) =>
        this += "Block(" ++= stats += ", " += expr += ")"
      case If(cond, thenp, elsep) =>
        this += "If(" += cond += ", " += thenp += ", " += elsep += ")"
      case Closure(meth, tpt) =>
        this += "Closure(" += meth += ", " += tpt += ")"
      case Match(selector, cases) =>
        this += "Match(" += selector += ", " ++= cases += ")"
      case GivenMatch(cases) =>
        this += "GivenMatch(" ++= cases += ")"
      case Return(expr, from) =>
        this += "Return(" += expr += ", " += from += ")"
      case While(cond, body) =>
        this += "While(" += cond += ", " += body += ")"
      case Try(block, handlers, finalizer) =>
        this += "Try(" += block += ", " ++= handlers += ", " += finalizer += ")"
      case Repeated(elems, elemtpt) =>
        this += "Repeated(" ++= elems += ", " += elemtpt += ")"
      case Inlined(call, bindings, expansion) =>
        this += "Inlined("
        visitOption(call, visitTree)
        this += ", " ++= bindings += ", " += expansion += ")"
      case ValDef(name, tpt, rhs) =>
        this += "ValDef(\"" += name += "\", " += tpt += ", " += rhs += ")"
      case DefDef(name, typeParams, paramss, returnTpt, rhs) =>
        this += "DefDef(\"" += name += "\", " ++= typeParams += ", " +++= paramss += ", " += returnTpt += ", " += rhs += ")"
      case TypeDef(name, rhs) =>
        this += "TypeDef(\"" += name += "\", " += rhs += ")"
      case ClassDef(name, constr, parents, derived, self, body) =>
        this += "ClassDef(\"" += name += "\", " += constr += ", "
        visitList[Tree](parents, visitTree)
        this += ", "
        visitList[TypeTree](derived, visitTree)
        this += ", " += self += ", " ++= body += ")"
      case Import(expr, selectors) =>
        this += "Import(" += expr += ", " ++= selectors += ")"
      case PackageClause(pid, stats) =>
        this += "PackageClause(" += pid += ", " ++= stats += ")"
      case Inferred() =>
        this += "Inferred()"
      case TypeIdent(name) =>
        this += "TypeIdent(\"" += name += "\")"
      case TypeSelect(qualifier, name) =>
        this += "TypeSelect(" += qualifier += ", \"" += name += "\")"
      case Projection(qualifier, name) =>
        this += "Projection(" += qualifier += ", \"" += name += "\")"
      case Singleton(ref) =>
        this += "Singleton(" += ref += ")"
      case Refined(tpt, refinements) =>
        this += "Refined(" += tpt += ", " ++= refinements += ")"
      case Applied(tpt, args) =>
        this += "Applied(" += tpt += ", " ++= args += ")"
      case ByName(result) =>
        this += "ByName(" += result += ")"
      case Annotated(arg, annot) =>
        this += "Annotated(" += arg += ", " += annot += ")"
      case LambdaTypeTree(tparams, body) =>
        this += "LambdaTypeTree(" ++= tparams += ", " += body += ")"
      case TypeBind(name, bounds) =>
        this += "TypeBind(" += name += ", " += bounds += ")"
      case TypeBlock(aliases, tpt) =>
        this += "TypeBlock(" ++= aliases += ", " += tpt += ")"
      case TypeBoundsTree(lo, hi) =>
        this += "TypeBoundsTree(" += lo += ", " += hi += ")"
      case WildcardTypeTree() =>
        this += s"WildcardTypeTree()"
      case MatchTypeTree(bound, selector, cases) =>
        this += "MatchTypeTree(" += bound += ", " += selector += ", " ++= cases += ")"
      case CaseDef(pat, guard, body) =>
        this += "CaseDef(" += pat += ", " += guard += ", " += body += ")"
      case TypeCaseDef(pat, body) =>
        this += "TypeCaseDef(" += pat += ", " += body += ")"
      case Bind(name, body) =>
        this += "Bind(\"" += name += "\", " += body += ")"
      case Unapply(fun, implicits, patterns) =>
        this += "Unapply(" += fun += ", " ++= implicits += ", " ++= patterns += ")"
      case Alternatives(patterns) =>
        this += "Alternative(" ++= patterns += ")"
    }

    def visitConstant(x: Constant): this.type = x match {
      case Constant.Unit() => this += "Constant.Unit()"
      case Constant.Null() => this += "Constant.Null()"
      case Constant.Boolean(value) => this += "Constant.Boolean(" += value += ")"
      case Constant.Byte(value) => this += "Constant.Byte(" += value += ")"
      case Constant.Short(value) => this += "Constant.Short(" += value += ")"
      case Constant.Int(value) => this += "Constant.Int(" += value += ")"
      case Constant.Long(value) => this += "Constant.Long(" += value += "L)"
      case Constant.Float(value) => this += "Constant.Float(" += value += "f)"
      case Constant.Double(value) => this += "Constant.Double(" += value += "d)"
      case Constant.Char(value) => this += "Constant.Char('" += value += "')"
      case Constant.String(value) => this += "Constant.String(\"" += value += "\")"
      case Constant.ClassOf(value) =>
        this += "Constant.ClassOf("
        visitType(value) += ")"
    }

    def visitType(x: TypeRepr): this.type = x match {
      case ConstantType(value) =>
        this += "ConstantType(" += value += ")"
      case TermRef(qual, name) =>
        this += "TermRef(" += qual+= ", \"" += name += "\")"
      case TypeRef(qual, name) =>
        this += "TypeRef(" += qual += ", \"" += name += "\")"
      case Refinement(parent, name, info) =>
        this += "Refinement(" += parent += ", \"" += name += "\", " += info += ")"
      case AppliedType(tycon, args) =>
        this += "AppliedType(" += tycon += ", " ++= args += ")"
      case AnnotatedType(underlying, annot) =>
        this += "AnnotatedType(" += underlying += ", " += annot += ")"
      case AndType(left, right) =>
        this += "AndType(" += left += ", " += right += ")"
      case OrType(left, right) =>
        this += "OrType(" += left += ", " += right += ")"
      case MatchType(bound, scrutinee, cases) =>
        this += "MatchType(" += bound += ", " += scrutinee += ", " ++= cases += ")"
      case ByNameType(underlying) =>
        this += "ByNameType(" += underlying += ")"
      case ParamRef(binder, idx) =>
        this += "ParamRef(" += binder += ", " += idx += ")"
      case ThisType(tp) =>
        this += "ThisType(" += tp += ")"
      case SuperType(thistpe, supertpe) =>
        this += "SuperType(" += thistpe += ", " += supertpe += ")"
      case RecursiveThis(binder) =>
        this += "RecursiveThis(" += binder += ")"
      case RecursiveType(underlying) =>
        this += "RecursiveType(" += underlying += ")"
      case MethodType(argNames, argTypes, resType) =>
        this += "MethodType(" ++= argNames += ", " ++= argTypes += ", " += resType += ")"
      case PolyType(argNames, argBounds, resType) =>
        this += "PolyType(" ++= argNames += ", " ++= argBounds += ", " += resType += ")"
      case TypeLambda(argNames, argBounds, resType) =>
        // resType is not printed to avoid cycles
        this += "TypeLambda(" ++= argNames += ", " ++= argBounds += ", _)"
      case TypeBounds(lo, hi) =>
        this += "TypeBounds(" += lo += ", " += hi += ")"
      case NoPrefix() =>
        this += "NoPrefix()"
    }

    def visitSignature(sig: Signature): this.type = {
      val Signature(params, res) = sig
      this += "Signature(" ++= params.map(_.toString) += ", " += res += ")"
    }

    def visitImportSelector(sel: ImportSelector): this.type = sel match {
      case SimpleSelector(id) => this += "SimpleSelector(" += id += ")"
      case RenameSelector(id1, id2) => this += "RenameSelector(" += id1 += ", " += id2 += ")"
      case OmitSelector(id) => this += "OmitSelector(" += id += ")"
    }

    def visitSymbol(x: Symbol): this.type =
      if x.isPackageDef  then this += "IsPackageDefSymbol(<" += x.fullName += ">)"
      else if x.isClassDef then this += "IsClassDefSymbol(<" += x.fullName += ">)"
      else if x.isDefDef then this += "IsDefDefSymbol(<" += x.fullName += ">)"
      else if x.isValDef then this += "IsValDefSymbol(<" += x.fullName += ">)"
      else if x.isTypeDef then this += "IsTypeDefSymbol(<" += x.fullName += ">)"
      else { assert(x.isNoSymbol); this += "NoSymbol()" }

    def +=(x: Boolean): this.type = { sb.append(x); this }
    def +=(x: Byte): this.type = { sb.append(x); this }
    def +=(x: Short): this.type = { sb.append(x); this }
    def +=(x: Int): this.type = { sb.append(x); this }
    def +=(x: Long): this.type = { sb.append(x); this }
    def +=(x: Float): this.type = { sb.append(x); this }
    def +=(x: Double): this.type = { sb.append(x); this }
    def +=(x: Char): this.type = { sb.append(x); this }
    def +=(x: String): this.type = { sb.append(x); this }

    def ++=(xs: List[String]): this.type = visitList[String](xs, +=)

    private implicit class StringOps(buff: self.type) {
      def +=(x: Option[String]): self.type = { visitOption(x, y => buff += "\"" += y += "\""); buff }
    }

    private implicit class TreeOps(buff: self.type) {
      def +=(x: Tree): self.type = { visitTree(x); buff }
      def +=(x: Option[Tree]): self.type = { visitOption(x, visitTree); buff }
      def ++=(x: List[Tree]): self.type = { visitList(x, visitTree); buff }
      def +++=(x: List[List[Tree]]): self.type = { visitList(x, ++=); buff }
    }

    private implicit class ConstantOps(buff: self.type) {
      def +=(x: Constant): self.type = { visitConstant(x); buff }
    }

    private implicit class TypeOps(buff: self.type) {
      def +=(x: TypeRepr): self.type = { visitType(x); buff }
      def +=(x: Option[TypeRepr]): self.type = { visitOption(x, visitType); buff }
      def ++=(x: List[TypeRepr]): self.type = { visitList(x, visitType); buff }
    }

    private implicit class SignatureOps(buff: self.type) {
      def +=(x: Option[Signature]): self.type = { visitOption(x, visitSignature); buff }
    }

    private implicit class ImportSelectorOps(buff: self.type) {
      def ++=(x: List[ImportSelector]): self.type = { visitList(x, visitImportSelector); buff }
    }

    private implicit class SymbolOps(buff: self.type) {
      def +=(x: Symbol): self.type = { visitSymbol(x); buff }
    }

    private def visitOption[U](opt: Option[U], visit: U => this.type): this.type = opt match {
      case Some(x) =>
        this += "Some("
        visit(x)
        this += ")"
      case _ =>
        this += "None"
    }

    private def visitList[U](list: List[U], visit: U => this.type): this.type = list match {
      case x0 :: xs =>
        this += "List("
        visit(x0)
        def visitNext(xs: List[U]): Unit = xs match {
          case y :: ys =>
            this += ", "
            visit(y)
            visitNext(ys)
          case Nil =>
        }
        visitNext(xs)
        this += ")"
      case Nil =>
        this += "Nil"
    }
  }

}
