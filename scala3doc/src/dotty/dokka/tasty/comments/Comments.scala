package dotty.dokka.tasty.comments

import scala.collection.immutable.SortedMap

import org.jetbrains.dokka.model.{doc => dkkd}

import com.vladsch.flexmark.util.{ast => mdu}
import com.vladsch.flexmark.formatter.Formatter
import com.vladsch.flexmark.util.options.MutableDataSet

import scala.tasty.Reflection
class Repr(val r: Reflection)(val sym: r.Symbol)

case class Comment (
  body:                    dkkd.DocTag,
  short:                   Option[dkkd.DocTag],
  authors:                 List[dkkd.DocTag],
  see:                     List[dkkd.DocTag],
  result:                  Option[dkkd.DocTag],
  throws:                  SortedMap[String, (dkkd.DocTag, dkkd.DocTag)],
  valueParams:             SortedMap[String, dkkd.DocTag],
  typeParams:              SortedMap[String, dkkd.DocTag],
  version:                 Option[dkkd.DocTag],
  since:                   Option[dkkd.DocTag],
  todo:                    List[dkkd.DocTag],
  deprecated:              Option[dkkd.DocTag],
  note:                    List[dkkd.DocTag],
  example:                 List[dkkd.DocTag],
  constructor:             Option[dkkd.DocTag],
  group:                   Option[dkkd.DocTag],
  // see comment in PreparsedComment below regarding these
  groupDesc:               SortedMap[String, dkkd.DocTag],
  groupNames:              SortedMap[String, dkkd.DocTag],
  groupPrio:               SortedMap[String, dkkd.DocTag],
  /** List of conversions to hide - containing e.g: `scala.Predef.FloatArrayOps` */
  hideImplicitConversions: List[dkkd.DocTag]
)

case class PreparsedComment (
  body:                    String,
  authors:                 List[String],
  see:                     List[String],
  result:                  List[String],
  throws:                  SortedMap[String, String],
  valueParams:             SortedMap[String, String],
  typeParams:              SortedMap[String, String],
  version:                 List[String],
  since:                   List[String],
  todo:                    List[String],
  deprecated:              List[String],
  note:                    List[String],
  example:                 List[String],
  constructor:             List[String],
  group:                   List[String],
  // NOTE these don't need to be sorted in principle, but code is nicer if they are
  groupDesc:               SortedMap[String, String],
  groupNames:              SortedMap[String, String],
  groupPrio:               SortedMap[String, String],
  hideImplicitConversions: List[String],
  shortDescription:        List[String],
  syntax:                  List[String],
)

case class DokkaCommentBody(summary: Option[dkkd.DocTag], body: dkkd.DocTag)

trait MarkupConversion[T] {
  protected def linkedExceptions(m: SortedMap[String, String]): SortedMap[String, (dkkd.DocTag, dkkd.DocTag)]
  protected def stringToMarkup(str: String): T
  protected def markupToDokka(t: T): dkkd.DocTag
  protected def markupToDokkaCommentBody(t: T): DokkaCommentBody
  protected def filterEmpty(xs: List[String]): List[T]
  protected def filterEmpty(xs: SortedMap[String, String]): SortedMap[String, T]

  private def single(annot: String, xs: List[String], filter: Boolean = true): Option[T] =
    (if (filter) filterEmpty(xs) else xs.map(stringToMarkup)) match {
      case x :: xs =>
        Some(x)
      case _ => None
    }

  final def parse(preparsed: PreparsedComment): Comment =
    val body = markupToDokkaCommentBody(stringToMarkup(preparsed.body))
    Comment(
      body                    = body.body,
      short                   = body.summary,
      authors                 = filterEmpty(preparsed.authors).map(markupToDokka),
      see                     = filterEmpty(preparsed.see).map(markupToDokka),
      result                  = single("@result", preparsed.result).map(markupToDokka),
      throws                  = linkedExceptions(preparsed.throws),
      valueParams             = filterEmpty(preparsed.valueParams).view.mapValues(markupToDokka).to(SortedMap),
      typeParams              = filterEmpty(preparsed.typeParams).view.mapValues(markupToDokka).to(SortedMap),
      version                 = single("@version", preparsed.version).map(markupToDokka),
      since                   = single("@since", preparsed.since).map(markupToDokka),
      todo                    = filterEmpty(preparsed.todo).map(markupToDokka),
      deprecated              = single("@deprecated", preparsed.deprecated, filter = false).map(markupToDokka),
      note                    = filterEmpty(preparsed.note).map(markupToDokka),
      example                 = filterEmpty(preparsed.example).map(markupToDokka),
      constructor             = single("@constructor", preparsed.constructor).map(markupToDokka),
      group                   = single("@group", preparsed.group).map(markupToDokka),
      groupDesc               = filterEmpty(preparsed.groupDesc).view.mapValues(markupToDokka).to(SortedMap),
      groupNames              = filterEmpty(preparsed.groupNames).view.mapValues(markupToDokka).to(SortedMap),
      groupPrio               = filterEmpty(preparsed.groupPrio).view.mapValues(markupToDokka).to(SortedMap),
      hideImplicitConversions = filterEmpty(preparsed.hideImplicitConversions).map(markupToDokka)
    )
}

class MarkdownCommentParser(repr: Repr)
    extends MarkupConversion[mdu.Document] {

  def stringToMarkup(str: String) =
    MarkdownParser.parseToMarkdown(str)

  def markupToDokka(md: mdu.Document) =
    MarkdownConverter(repr).convertDocument(md)

  def markupToDokkaCommentBody(md: mdu.Document) =
    val converter = MarkdownConverter(repr)
    DokkaCommentBody(
      summary = converter.extractAndConvertSummary(md),
      body = converter.convertDocument(md),
    )

  def linkedExceptions(m: SortedMap[String, String]) = {
    val c = MarkdownConverter(repr)
    m.map { case (targetStr, body) =>
      targetStr -> (c.resolveLinkQuery(targetStr, ""), dkk.text(body))
    }
  }

  def filterEmpty(xs: List[String]) = {
    xs.map(_.trim)
      .filterNot(_.isEmpty)
      .map(stringToMarkup)
  }

  def filterEmpty(xs: SortedMap[String,String]) =
    xs.view.mapValues(_.trim)
      .filterNot { case (_, v) => v.isEmpty }
      .mapValues(stringToMarkup).to(SortedMap)
}

case class WikiCommentParser(repr: Repr)
    extends MarkupConversion[wiki.Body] {

  def stringToMarkup(str: String) =
    wiki.Parser(str).document()

  def markupToDokka(body: wiki.Body) =
    wiki.Converter(repr).convertBody(body)

  def markupToDokkaCommentBody(body: wiki.Body) =
    val converter = wiki.Converter(repr)
    DokkaCommentBody(
      summary = body.summary.map(converter.convertBody),
      body = converter.convertBody(body),
    )

  def linkedExceptions(m: SortedMap[String, String]) = {
    m.map { case (targetStr, body) =>
      val c = wiki.Converter(repr)
      targetStr -> (c.resolveLinkQuery(targetStr, None), c.convertBody(stringToMarkup(body)))
    }
  }

  def filterEmpty(xs: List[String]) =
    xs.map(stringToMarkup)

  def filterEmpty(xs: SortedMap[String,String]) =
    xs.view.mapValues(stringToMarkup).to(SortedMap)
      .filterNot { case (_, v) => v.blocks.isEmpty }

}
