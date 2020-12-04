package dotty.dokka

import org.jetbrains.dokka.base.signatures._
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.{ TypeParameter => _, _ }
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.pages._
import collection.JavaConverters._
import dotty.dokka.model.api.{Kind, _}

case class InlineSignatureBuilder(names: Signature = Nil, preName: Signature = Nil) extends SignatureBuilder:
  override def text(str: String): SignatureBuilder = copy(names = str +: names)
  override def name(str: String, dri: DRI): SignatureBuilder = copy(names = Nil, preName = names)
  override def driLink(text: String, dri: DRI): SignatureBuilder = copy(names = Link(text, dri) +: names)
  override def signature(s: Signature): SignatureBuilder = copy(names = s.reverse ++ names)

object InlineSignatureBuilder:
  def typeSignatureFor(d: Documentable): Signature =
      ScalaSignatureProvider.rawSignature(d, InlineSignatureBuilder()).asInstanceOf[InlineSignatureBuilder].names.reverse

trait SignatureBuilder extends ScalaSignatureUtils {
  def text(str: String): SignatureBuilder
  def name(str: String, dri: DRI) = driLink(str, dri)
  def driLink(text: String, dri: DRI): SignatureBuilder
  def signature(s: Signature): SignatureBuilder = s.foldLeft(this){
    case (bld, s: String) => bld.text(s)
    case (bld, Link(text: String, dri: DRI)) => bld.driLink(text, dri)
  }

  // Support properly once we rewrite signature builder
  def memberName(name: String, dri: DRI) = text(name)

  def list[E](
      elements: Seq[E],
      prefix: String = "",
      suffix: String = "",
      separator: String = ", ",
    )(
      elemOp: (SignatureBuilder, E) => SignatureBuilder
    ): SignatureBuilder = elements match {
      case Nil => this
      case head :: tail =>
        tail.foldLeft(elemOp(text(prefix), head))((b, e) => elemOp(b.text(separator), e)).text(suffix)
    }

  def annotationsBlock(d: Member): SignatureBuilder =
      d.annotations.foldLeft(this){ (bdr, annotation) => bdr.buildAnnotation(annotation)}

    def annotationsInline(d: Parameter): SignatureBuilder =
        d.annotations.foldLeft(this){ (bdr, annotation) => bdr.buildAnnotation(annotation) }

    private def buildAnnotation(a: Annotation): SignatureBuilder =
       text("@").driLink(a.dri.getClassNames, a.dri).buildAnnotationParams(a).text(" ")

    private def buildAnnotationParams(a: Annotation): SignatureBuilder =
      if !a.params.isEmpty then
        list(a.params, "(", ")", ", "){ (bdr, param) => bdr.buildAnnotationParameter(param)}
      else this

    private def addParameterName(txt: Option[String]): SignatureBuilder = txt match {
        case Some(name) => this.text(s"$txt = ")
        case _ => this
      }

    private def buildAnnotationParameter(a: Annotation.AnnotationParameter): SignatureBuilder = a match {
      case Annotation.PrimitiveParameter(name, value) =>
        addParameterName(name).text(value)
      case Annotation.LinkParameter(name, dri, text) =>
        addParameterName(name).driLink(text, dri)
      case Annotation.UnresolvedParameter(name, value) =>
        addParameterName(name).text(value)
    }

    def modifiersAndVisibility(t: Member, kind: String) =
      val (prefixMods, suffixMods) = t.modifiers.partition(_.prefix)
      val all = prefixMods.map(_.name) ++ Seq(t.visibility.asSignature) ++ suffixMods.map(_.name)

      text(all.toSignatureString()).text(kind + " ")

    def generics(on: Seq[TypeParameter]) = list(on.toList, "[", "]"){ (bdr, e) =>
      bdr.text(e.variance).memberName(e.name, e.dri).signature(e.signature)
    }

    def functionParameters(params: Seq[Seq[Parameter]]) =
      if params.isEmpty then this.text("")
      else if params == List(Nil) then this.text("()")
      else this.list(params, separator = ""){ (bld, pList) =>
        bld.list(pList, "(", ")"){ (bld, p) =>
            bld.annotationsInline(p)
              .text(p.modifiers)
              .memberName(p.name, p.dri)
              .text(": ")
              .signature(p.signature)
        }
      }
}

trait ScalaSignatureUtils:
  extension (tokens: Seq[String]) def toSignatureString(): String =
    tokens.filter(_.trim.nonEmpty).mkString(""," "," ")
