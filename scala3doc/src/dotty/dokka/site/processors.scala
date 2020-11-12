package dotty.dokka
package site

import java.io.File
import java.nio.file.Files
import java.nio.file.FileVisitOption

import org.jetbrains.dokka.base.renderers.html.{NavigationNode, NavigationPage}
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.pages._
import org.jetbrains.dokka.transformers.pages.PageTransformer

import scala.collection.JavaConverters._
import dotty.dokka.model.api._

abstract class BaseStaticSiteProcessor(staticSiteContext: Option[StaticSiteContext]) extends PageTransformer:
  final override def invoke(input: RootPageNode): RootPageNode = staticSiteContext.fold(input)(transform(input, _))

  protected def transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode

class SiteResourceManager(ctx: Option[StaticSiteContext]) extends BaseStaticSiteProcessor(ctx):
  private def listResources(nodes: Seq[PageNode]): Set[String] =
    nodes.flatMap {
      case it: StaticPageNode => listResources(it.getChildren.asScala.toList) ++ it.resources()
      case _ => Seq.empty
    }.toSet

  override def transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode =
    val rootPath = ctx.root.toPath
    val imgPath = rootPath.resolve("images")
    val images =
      if !Files.exists(imgPath) then Nil
      else
        val allPaths = Files.walk(imgPath, FileVisitOption.FOLLOW_LINKS)
        val files = allPaths.filter(Files.isRegularFile(_)).iterator().asScala
        files.map(p => rootPath.relativize(p).toString).toList

    val resources = images ++ listResources(input.getChildren.asScala.toList)
    val resourcePages = resources.map { path =>
      val strategy = new RenderingStrategy.Copy(rootPath.resolve(path).toString)
      new RendererSpecificResourcePage(path, JList(), strategy)
    }

    val modified = input.transformContentPagesTree {
      case it: StaticPageNode =>
        it.copy(getEmbeddedResources =
          if it.template.hasFrame() then it.getEmbeddedResources ++ it.resources().asJava
          else it.resources().asJava
        )
      case it => it
    }
    modified.modified(modified.getName, (resourcePages ++ modified.getChildren.asScala).asJava)

case class AContentPage(
  override val getName: String,
  override val getChildren: JList[PageNode],
  override val getContent: ContentNode,
  override val getDri: JSet[DRI],
  override val getEmbeddedResources: JList[String] = JList(),
) extends ContentPage:
  override def getDocumentable: Documentable = null

  override def modified(
    name: String,
    content: ContentNode,
    dri: JSet[DRI],
    embeddedResources: JList[String],
    children: JList[_ <: PageNode]
  ): ContentPage = copy(name, children.asInstanceOf[JList[PageNode]], content, dri, embeddedResources)

  override def modified(name: String, children: JList[_ <: PageNode]): PageNode =
    copy(name, getChildren = children.asInstanceOf[JList[PageNode]])

class SitePagesCreator(ctx: Option[StaticSiteContext]) extends BaseStaticSiteProcessor(ctx):
  private def processRootPage(input: RootPageNode, children: List[PageNode] = Nil): AContentPage = input match
    case input: ContentPage =>
      AContentPage(
        input.getName,
        children.asJava,
        input.getContent,
        JSet(apiPageDRI),
        input.getEmbeddedResources
      )
    case _: RendererSpecificRootPage =>
      children.filter(_.isInstanceOf[RootPageNode]) match
          case List(nestedRoot: RootPageNode) =>
            processRootPage(nestedRoot, children.filter { _ != nestedRoot } ++ nestedRoot.getChildren.asScala)
          case other =>
            throw new RuntimeException(s"Expected single nested roor but get: $other")

    case _ => throw new RuntimeException(s"UNSUPPORTED! ${input.getClass.getName}")

  override def transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode =
    val (contentPage, others) = input.getChildren.asScala.toList.partition { _.isInstanceOf[ContentPage] }
    val modifiedModuleRoot = processRootPage(input, contentPage)
    val (indexes, children) = ctx.allPages.partition(_.template.isIndexPage())
    // TODO (https://github.com/lampepfl/scala3doc/issues/238): provide proper error handling
    if (indexes.size > 1) println("ERROR: Multiple index pages found $indexes}")

    val rootContent = indexes.headOption.fold(ctx.asContent(Text(), mkDRI(extra = "root_content")).get(0))(_.getContent)

    val root = AContentPage(
      input.getName,
      (List(modifiedModuleRoot.modified("API", modifiedModuleRoot.getChildren)) ++ children).asJava,
      rootContent,
      JSet(docsDRI),
      JList()
    )

    new RendererSpecificRootPage(
      modifiedModuleRoot.getName,
      (List(root) ++ others).asJava,
      RenderingStrategy.DoNothing.INSTANCE
    )

class RootIndexPageCreator(ctx: Option[StaticSiteContext]) extends BaseStaticSiteProcessor(ctx):
  override def transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode =
    ctx.indexPage().fold(input){ it =>
      val (contentNodes, nonContent) = input.getChildren.asScala.partition { _.isInstanceOf[ContentNode] }
      val (navigations, rest) = nonContent.partition { _.isInstanceOf[NavigationPage] }
      val modifiedNavigation = navigations.map { it =>
        val sourceSets = it.asInstanceOf[NavigationPage].getRoot.getSourceSets
        def flatMapPackages(pn: PageNode): List[NavigationNode] =
          def processChildren = pn.getChildren.asScala.flatMap(flatMapPackages).toList
          pn match
            case cp: ContentPage => cp.getDocumentable match
              case null =>
                processChildren
              case p: DPackage =>
                List(new NavigationNode(p.getName, p.getDri, sourceSets, JList())) ++ processChildren
              case other =>
                Nil
            case _ =>
              Nil

        val packagesNavigation = input.getChildren.asScala.flatMap(flatMapPackages).sortBy(_.getName)
        val api = new NavigationNode("API", apiPageDRI, sourceSets, packagesNavigation.asJava)

        def toNavigationNode(page: StaticPageNode): NavigationNode = NavigationNode(
            page.title(),
            page.getDri.asScala.head,
            sourceSets,
            page.getChildren.asScala.collect { case p: StaticPageNode => toNavigationNode(p)}.asJava
          )

        new NavigationPage(
          new NavigationNode(
            input.getName,
            docsRootDRI,
            sourceSets,
            (ctx.mainPages.map(toNavigationNode) ++ Seq(api)).asJava
          )
        )
      }
      val newRoot = it.copy(getDri =  JSet(docsRootDRI), getChildren = contentNodes.asJava)
      input.modified(input.getName, (List(newRoot) ++ rest ++ modifiedNavigation).asJava)
    }
