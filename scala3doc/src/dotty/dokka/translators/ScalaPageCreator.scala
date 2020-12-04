package dotty.dokka

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.chaining._
import org.jetbrains.dokka.base.translators.documentables.{DefaultPageCreator, PageContentBuilder}
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder$DocumentableContentBuilder
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.transformers.documentation.DocumentableToPageTranslator
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.dokka.model._
import org.jetbrains.dokka.pages._
import collection.JavaConverters._
import org.jetbrains.dokka.model.properties._
import org.jetbrains.dokka.base.transformers.documentables.CallableExtensions
import org.jetbrains.dokka.DokkaConfiguration$DokkaSourceSet
import org.jetbrains.dokka.base.resolvers.anchors._
import org.jetbrains.dokka.model.doc._
import dotty.dokka.model.api._
import dotty.dokka.model.api.Kind
import dotty.dokka.model.api.Link

type DocBuilder = ScalaPageContentBuilder#ScalaDocumentableContentBuilder

class ScalaPageCreator(
  commentsToContentConverter: CommentsToContentConverter,
  signatureProvider: SignatureProvider,
)(using ctx: DocContext)
  extends DefaultPageCreator(commentsToContentConverter, signatureProvider, ctx.logger):

  private val contentBuilder =
    ScalaPageContentBuilder(commentsToContentConverter, signatureProvider)

  override def pageForModule(m: DModule): ModulePageNode = super.pageForModule(m)

  private def pagesForMembers(member: Member): JList[PageNode] =
    val (all, _) = member.membersBy(_.kind.isInstanceOf[Classlike])
    all.map(pageForMember(_)).asJava

  override def pageForPackage(p: DPackage): PackagePageNode =
    PackagePageNode(
      p.name,
      contentForPackage(p),
      JSet(p.dri),
      p,
      pagesForMembers(p),
      JNil
    )

  override def pageForClasslike(c: DClasslike): ClasslikePageNode = ???

  def pageForMember(c: Member): ClasslikePageNode = {
    val name =
      if c.kind == Kind.Object && c.companion.isDefined then
        c.getName + "$"
      else c.getName

    // Hack, need our own page!
    ClasslikePageNode(
      name,
      contentForClass(c.asInstanceOf[DClass]),
      JSet(c.getDri),
      c.asInstanceOf[DClass],
      JNil,
      JNil,
    ).modified(name, pagesForMembers(c)) // We need override default page
  }

  override def pageForFunction(f: DFunction) = super.pageForFunction(f)

  override def contentForModule(m: DModule) = {
    def buildBlock = (builder: DocBuilder) => builder
      .group(kind = ContentKind.Cover) { gbuilder => gbuilder
        .cover(m.getName)()
        .descriptionIfNotEmpty(m)
      }
      .addChildren(contentForComments(m).asScala.toSeq)
      .groupingBlock(
        "Packages",
        List("" -> m.getPackages.asScala.toList),
        kind = ContentKind.Packages,
        sourceSets = m.getSourceSets.asScala.toSet
      )(
        (bdr, elem) => bdr
      ) { (bdr, elem) => bdr
        .driLink(elem.getName, elem.getDri)
      }

    contentBuilder.contentForDocumentable(m, buildBlock = buildBlock)
  }

  override def contentForPackage(p: DPackage) = {
    def buildBlock = (builder: DocBuilder) => builder
      .group(kind = ContentKind.Cover) { gbuilder => gbuilder
        .cover(p.getName)()
        .descriptionIfNotEmpty(p)
      }
      .documentableFilter()
      .group(styles = Set(ContentStyle.TabbedContent)) { b => b
        .contentForScope(p)
      }

    contentBuilder.contentForDocumentable(p, buildBlock = buildBlock)
  }

  override def contentForClasslike(c: DClasslike) = throw UnsupportedOperationException(
      s"Unable to generate DClasslike using default dokka method for $c!")

  def contentForClass(c: DClass) = {
    def buildBlock = (builder: DocBuilder) => builder
      .group(kind = ContentKind.Cover, sourceSets = c.getSourceSets.asScala.toSet) { gbdr => gbdr
        .cover(c.getName)()
        .sourceSetDependentHint(Set(c.getDri), c.getSourceSets.asScala.toSet) { sbdr => sbdr
          .signature(c)
          .contentForDescription(c)
        }
      }
      .documentableFilter()
      .group(styles = Set(ContentStyle.TabbedContent)) { b => b
        .contentForScope(c)
        .contentForEnum(c)
        .contentForConstructors(c)
        .contentForTypesInfo(c)
      }
    contentBuilder.contentForDocumentable(c, buildBlock = buildBlock)
  }


  extension (b: DocBuilder):
    def descriptionIfNotEmpty(d: Documentable): DocBuilder = {
      val desc = this.contentForDescription(d).asScala.toSeq
      val res = if desc.isEmpty then b else b
        .sourceSetDependentHint(
          Set(d.getDri),
          d.getSourceSets.asScala.toSet,
          kind = ContentKind.SourceSetDependentHint,
          styles = Set(TextStyle.UnderCoverText)
        ) { sourceSetBuilder => sourceSetBuilder
            .addChildren(desc)
        }
      res
    }

    def contentForComments(d: Documentable) = b

    def contentForDescription(d: Documentable) = {
      val specialTags = Set[Class[_]](classOf[Description])

      type SourceSet = DokkaConfiguration$DokkaSourceSet

      val tags: List[(SourceSet, TagWrapper)] =
        d.getDocumentation.asScala.toList.flatMap( (pd, doc) => doc.getChildren.asScala.map(pd -> _).toList )

      val platforms = d.getSourceSets.asScala.toSet

      val description = tags.collect{ case (pd, d: Description) => (pd, d) }.drop(1).groupBy(_(0)).map( (key, value) => key -> value.map(_(1)))

      /** Collect the key-value pairs from `iter` into a `Map` with a `cleanup` step,
        * keeping the original order of the pairs.
        */
      def collectInMap[K, E, V](
        iter: Iterator[(K, E)]
      )(
        cleanup: List[E] => V
      ): collection.Map[K, V] = {
        val lhm = mutable.LinkedHashMap.empty[K, ListBuffer[E]]
        iter.foreach { case (k, e) =>
          lhm.updateWith(k) {
            case None => Some(ListBuffer.empty.append(e))
            case Some(buf) =>
              buf.append(e)
              Some(buf)
          }
        }
        lhm.iterator.map { case (key, buf) => key -> cleanup(buf.result)}.to(mutable.LinkedHashMap)
      }

      val unnamedTags: collection.Map[(SourceSet, Class[_]), List[TagWrapper]] =
        collectInMap {
          tags.iterator
            .filterNot { t =>
              t(1).isInstanceOf[NamedTagWrapper] || specialTags.contains(t(1).getClass)
            }.map { t =>
              (t(0), t(1).getClass) -> t(1)
            }
        }(cleanup = identity)

      val namedTags: collection.Map[
        String,
        Either[
          collection.Map[SourceSet, NamedTagWrapper],
          collection.Map[(SourceSet, String), ScalaTagWrapper.NestedNamedTag],
        ],
      ] = {
        val grouped = collectInMap {
          tags.iterator.collect {
            case (sourcesets, n: NamedTagWrapper) =>
              (n.getName, n.isInstanceOf[ScalaTagWrapper.NestedNamedTag]) -> (sourcesets, n)
          }
        }(cleanup = identity)

        grouped.iterator.map {
          case ((name, true), values) =>
            val groupedValues =
              values.iterator.map {
                case (sourcesets, t) =>
                  val tag = t.asInstanceOf[ScalaTagWrapper.NestedNamedTag]
                  (sourcesets, tag.subname) -> tag
              }.to(mutable.LinkedHashMap)
            name -> Right(groupedValues)
          case ((name, false), values) =>
            name -> Left(values.to(mutable.LinkedHashMap))
        }.to(mutable.LinkedHashMap)
      }

      b.group(Set(d.getDri), styles = Set(TextStyle.Block, TableStyle.Borderless)) { bdr =>
        val b1 = description.foldLeft(bdr){
          case (bdr, (key, value)) => bdr
              .group(sourceSets = Set(key)){ gbdr =>
                value.foldLeft(gbdr) { (gbdr, tag) => gbdr
                  .comment(tag.getRoot)
                }
              }
        }

        b1.table(kind = ContentKind.Comment, styles = Set(TableStyle.DescriptionList)){ tbdr =>
          val withUnnamedTags = unnamedTags.foldLeft(tbdr){ case (bdr, (key, value) ) => bdr
            .cell(sourceSets = Set(key(0))){ b => b
              .text(key(1).getSimpleName, styles = Set(TextStyle.Bold))
            }
            .cell(sourceSets = Set(key(0))) { b => b
              .list(value, separator = ""){ (bdr, elem) => bdr
                .comment(elem.getRoot)
              }
            }
          }

          val withNamedTags = namedTags.foldLeft(withUnnamedTags){
            case (bdr, (key, Left(value))) =>
              value.foldLeft(bdr){ case (bdr, (sourceSets, v)) => bdr
                .cell(sourceSets = Set(sourceSets)){ b => b
                  .text(key)
                }
                .cell(sourceSets = Set(sourceSets)){ b => b
                  .comment(v.getRoot)
                }
              }
            case (bdr, (key, Right(groupedValues))) => bdr
              .cell(sourceSets = d.getSourceSets.asScala.toSet){ b => b
                .text(key)
              }
              .cell(sourceSets = d.getSourceSets.asScala.toSet)(_.table(kind = ContentKind.Comment, styles = Set(TableStyle.NestedDescriptionList)){ tbdr =>
                groupedValues.foldLeft(tbdr){ case (bdr, ((sourceSets, _), v)) => bdr
                  .cell(sourceSets = Set(sourceSets)){ b => b
                    .comment(v.identTag)
                  }
                  .cell(sourceSets = Set(sourceSets)){ b => b
                    .comment(v.descTag)
                  }
                }
              })
          }

          val withCompanion = d.companion.fold(withNamedTags){ co => withNamedTags
                .cell(sourceSets = d.getSourceSets.asScala.toSet){ b => b
                  .text("Companion")
                }
                .cell(sourceSets = d.getSourceSets.asScala.toSet){ b => b
                  .driLink(
                    d.kind match {
                      case Kind.Object => "class"
                      case _ => "object"
                    },
                    co
                  )
                }
              }

          val withExtensionInformation = d.kind match {
            case Kind.Extension(on, _) =>
              val sourceSets = d.getSourceSets.asScala.toSet
              withCompanion.cell(sourceSets = sourceSets)(_.text("Extension"))
                .cell(sourceSets = sourceSets)(_.text(s"This function is an extension on (${on.name}: ").inlineSignature(d, on.signature).text(")"))
            case _ => withCompanion
          }

          d match
            case null => withExtensionInformation
            case m: Member =>
              ctx.sourceLinks.pathTo(m).fold(withCompanion){ link =>
                val sourceSets = m.getSourceSets.asScala.toSet
                withExtensionInformation.cell(sourceSets = sourceSets)(_.text("Source"))
                  .cell(sourceSets = sourceSets)(_.resolvedLink("(source)", link))
              }
        }
      }
    }

    def contentForScope(s: Documentable & WithScope & WithExtraProperties[_]) =
      def groupExtensions(extensions: Seq[Member]): Seq[DocumentableSubGroup] =
        extensions.groupBy(_.kind).map {
          case (Kind.Extension(on, _), members) =>
            val signature = Signature(s"extension (${on.name}: ") join on.signature join Signature(")")
            DocumentableSubGroup(signature, members.toSeq)
          case other => sys.error(s"unexpected value: $other")
        }.toSeq

      val (definedMethods, inheritedMethods) = s.membersBy(_.kind.isInstanceOf[Kind.Def])
      val (definedFields, inheritedFiles) = s.membersBy(m => m.kind == Kind.Val || m.kind == Kind.Var)
      val (definedClasslikes, inheritedClasslikes) = s.membersBy(m => m.kind.isInstanceOf[Classlike])
      val (definedTypes, inheritedTypes) = s.membersBy(_.kind.isInstanceOf[Kind.Type])
      val (definedGivens, inheritedGives) = s.membersBy(_.kind.isInstanceOf[Kind.Given])
      val (definedExtensions, inheritedExtensions) = s.membersBy(_.kind.isInstanceOf[Kind.Extension])
      val exports = s.allMembers.filter(_.kind.isInstanceOf[Kind.Exported])
      val (definedImplicits, inheritedImplicits) = s.membersBy(_.kind.isInstanceOf[Kind.Implicit])

      b
        .contentForComments(s)
        .documentableTab("Type members")(
          DocumentableGroup(Some("Types"), definedTypes),
          DocumentableGroup(Some("Classlikes"), definedClasslikes),
          DocumentableGroup(Some("Inherited types"), inheritedTypes),
          DocumentableGroup(Some("Inherited classlikes"), inheritedClasslikes)
        )
        .documentableTab("Methods")(
          DocumentableGroup(Some("Defined methods"), definedMethods),
          DocumentableGroup(Some("Inherited methods"),  inheritedMethods),
        )
        .documentableTab("Value members")(
          DocumentableGroup(Some("Defined value members"), definedFields),
          DocumentableGroup(Some("Inherited value members"), inheritedFiles)
        )
        .documentableTab("Givens")(
          DocumentableGroup(Some("Defined givens"), definedGivens),
          DocumentableGroup(Some("Inherited givens"), inheritedGives)
        )
        .documentableTab("Extensions")(
          DocumentableGroup(Some("Defined extensions"), groupExtensions(definedExtensions)),
          DocumentableGroup(Some("Inherited extensions"), groupExtensions(inheritedExtensions))
        )
        .documentableTab("Implicits")(
          DocumentableGroup(Some("Defined implicits"), definedImplicits),
          DocumentableGroup(Some("Inherited implicits"), inheritedImplicits)
        )
        .documentableTab("Exports")(
          DocumentableGroup(Some("Defined exports"), exports)
        )


    def contentForEnum(c: DClass) =
      b.documentableTab("Enum entries")(
        DocumentableGroup(None, c.membersBy(_.kind == Kind.EnumCase)._1) // Enum entries cannot be inherited
      )


    def contentForConstructors(c: DClass) =
       b.documentableTab("Constructors")(
        DocumentableGroup(None, c.membersBy(_.kind.isInstanceOf[Kind.Constructor])._1)
      )


    def contentForTypesInfo(c: DClass) =
      val supertypes = c.parents
      val subtypes = c.knownChildren
      val graph = MemberExtension.getFrom(c).map(_.graph)

      def contentForTypeLink(builder: DocBuilder, link: LinkToType): DocBuilder =
        builder.group(styles = Set(TextStyle.Paragraph)) { builder =>
          link.signature.foldLeft(builder.text(link.kind.name).text(" ")){ (builder, sigElement) => sigElement match
            case Link(name, dri) => builder.driLink(name, dri)
            case str: String => builder.text(str)
          }
        }

      val withSupertypes = if supertypes.isEmpty then b else
        b.header(2, "Linear supertypes")()
          .group(
            kind = ContentKind.Comment,
            styles = Set(ContentStyle.WithExtraAttributes),
            extra = PropertyContainer.Companion.empty plus SimpleAttr.Companion.header("Linear supertypes")
        ){ gbdr => gbdr
            .group(kind = ContentKind.Symbol, styles = Set(TextStyle.Monospace)){ grbdr => grbdr
              .list(supertypes.toList, separator = "")(contentForTypeLink)
            }
          }

      val withSubtypes = if (subtypes.isEmpty) withSupertypes else
        withSupertypes.header(2, "Known subtypes")()
          .group(
            kind = ContentKind.Comment,
            styles = Set(ContentStyle.WithExtraAttributes),
            extra = PropertyContainer.Companion.empty plus SimpleAttr.Companion.header("Known subtypes")
          ) { _.group(kind = ContentKind.Symbol, styles = Set(TextStyle.Monospace)) {
              _.list(subtypes.toList, separator="")(contentForTypeLink)
            }
          }

      graph.fold(withSubtypes) { graph =>
        if graph.edges.isEmpty then withSubtypes else
          withSubtypes.header(2, "Type hierarchy")().group(
            kind = ContentKind.Comment,
            styles = Set(ContentStyle.WithExtraAttributes),
            extra = PropertyContainer.Companion.empty plus SimpleAttr.Companion.header("Type hierarchy")
          ) { _.group(kind = ContentKind.Symbol, styles = Set(TextStyle.Monospace)) {
              _.dotDiagram(graph)
            }
          }
      }
