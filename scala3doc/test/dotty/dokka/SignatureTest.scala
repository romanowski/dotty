package dotty.dokka

import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

import org.jetbrains.dokka.pages.{RootPageNode, PageNode, ContentPage, ContentText, ContentNode, ContentComposite}

import dotty.dokka.model.api.Link

private enum Signature:
  case Expected(name: String, signature: String)
  case Unexpected(name: String)
import Signature._

abstract class SignatureTest(
  testName: String, 
  signatureKinds: Seq[String], 
  sourceFiles: List[String] = Nil,
  ignoreMissingSignatures: Boolean = false
) extends ScaladocTest(testName):
  override def assertions = Assertion.AfterPagesTransformation { root =>
    val sources = sourceFiles match
      case Nil => testName :: Nil
      case s => s

    val allSignaturesFromSources = sources
      .map { file => Source.fromFile(s"src/main/scala/tests/$file.scala") }
      .flatMap(signaturesFromSources(_, signatureKinds))
      .toList
    val expectedFromSources: Map[String, List[String]] = allSignaturesFromSources
      .collect { case Expected(name, signature) => name -> signature }
      .groupMap(_._1)(_._2)
    val unexpectedFromSources: Set[String] = allSignaturesFromSources.collect { case Unexpected(name) => name }.toSet
    
    val actualSignatures: Map[String, Seq[String]] = signaturesFromDocumentation(root).flatMap { signature =>
      findName(signature, signatureKinds).map(_ -> signature)
    }.groupMap(_._1)(_._2)

    val unexpected = unexpectedFromSources.flatMap(actualSignatures.getOrElse(_, Nil))
    val expectedButNotFound = expectedFromSources.flatMap { 
      case (k, v) => findMissingSingatures(v, actualSignatures.getOrElse(k, Nil)) 
    }

    val missingReport = Option.when(!ignoreMissingSignatures && !expectedButNotFound.isEmpty)
      (s"Not documented signatures:\n${expectedButNotFound.mkString("\n")}")

    val unexpectedReport = Option.when(!unexpected.isEmpty)
      (s"Unexpectedly documented signatures:\n${unexpected.mkString("\n")}")

  } :: Nil

// e.g. to remove '(0)' from object IAmACaseObject extends CaseImplementThis/*<-*/(0)/*->*/ 
private val commentRegex = raw"\/\*<-\*\/[^\/]+\/\*->\*\/".r
private val whitespaceRegex = raw"\s+".r
private val expectedRegex = raw".+//expected: (.+)".r
private val unexpectedRegex = raw"(.+)//unexpected".r
private val identifierRegex = raw"^\s*(`.*`|(?:\w+)(?:_[^\[\(\s]+)|\w+|[^\[\(\s]+)".r

private def findMissingSingatures(expected: Seq[String], actual: Seq[String]): Set[String] = 
  expected.toSet &~ actual.toSet

extension (s: String):
  private def startWithAnyOfThese(c: String*) = c.exists(s.startsWith)
  private def compactWhitespaces = whitespaceRegex.replaceAllIn(s, " ")

private def findName(signature: String, kinds: Seq[String]): Option[String] = 
  for
    kindMatch <- kinds.flatMap(k => s"\\b$k\\b".r.findFirstMatchIn(signature)).headOption
    afterKind <- Option(kindMatch.after(0)) // to filter out nulls
    nameMatch <- identifierRegex.findFirstMatchIn(afterKind)
  yield nameMatch.group(1)

private def signaturesFromSources(source: Source, kinds: Seq[String]): Seq[Signature] =
  source.getLines.map(_.trim)
      .filterNot(_.isEmpty)
      .filterNot(_.startWithAnyOfThese("=",":","{","}", "//"))
      .toSeq
      .flatMap {
        case unexpectedRegex(signature) => findName(signature, kinds).map(Unexpected(_))
        case expectedRegex(signature) => findName(signature, kinds).map(Expected(_, signature))
        case signature => 
          findName(signature, kinds).map(Expected(_, commentRegex.replaceAllIn(signature, "").compactWhitespaces))
      }

private def signaturesFromDocumentation(root: PageNode): Seq[String] = 
  def flattenToText(node: ContentNode) : Seq[String] = node match
    case t: ContentText => Seq(t.getText)
    case c: ContentComposite => 
        c.getChildren.asScala.flatMap(flattenToText).toSeq
    case l: DocumentableElement => 
        (l.annotations ++ Seq(" ") ++ l.modifiers ++ Seq(l.name) ++ l.signature).map {
            case s: String => s
            case (s: String, _) => s
            case Link(s: String, _) => s
        }        
    case _ => Seq()

  def all(p: ContentNode => Boolean)(n: ContentNode): Seq[ContentNode] =
      if p(n) then Seq(n) else n.getChildren.asScala.toSeq.flatMap(all(p))

  def (page: PageNode).allPages: List[PageNode] = page :: page.getChildren.asScala.toList.flatMap(_.allPages)
      
  val nodes = root.allPages
    .collect { case p: ContentPage => p }
    .flatMap(p => all(_.isInstanceOf[DocumentableElement])(p.getContent))
  nodes.map(flattenToText(_).mkString.trim)
