package dotty.dokka
package site

import java.io.File
import java.nio.file.Files
import java.nio.file.FileVisitOption
import java.nio.file.Path
import java.nio.file.Paths

import org.jetbrains.dokka.base.parsers.MarkdownParser
import org.jetbrains.dokka.base.transformers.pages.comments.DocTagToContentConverter
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.doc.{DocTag, Text}
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.{ContentKind, ContentNode, DCI, PageNode}
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.pages.Style
import org.jetbrains.dokka.model.DisplaySourceSet
import util.Try

import scala.collection.JavaConverters._

class StaticSiteContext(val root: File, sourceSets: Set[SourceSetWrapper], val args: Scala3doc.Args, val sourceLinks: SourceLinks):

  var memberLinkResolver: String => Option[DRI] = _ => None

  def indexPage():Option[StaticPageNode] =
    val files = List(new File(root, "index.html"), new File(root, "index.md")).filter { _.exists() }
    // TODO (https://github.com/lampepfl/scala3doc/issues/238): provide proper error handling
    if (files.size > 1) println(s"ERROR: Multiple root index pages found: ${files.map(_.getAbsolutePath)}")
    files.flatMap(loadTemplate(_, isBlog = false)).headOption.map(templateToPage)

  lazy val layouts: Map[String, TemplateFile] =
    val layoutRoot = new File(root, "_layouts")
    val dirs: Array[File] = Option(layoutRoot.listFiles()).getOrElse(Array())
    dirs.map { it => loadTemplateFile(it) }.map { it => it.name -> it }.toMap

  lazy val sideBarConfig =
    val sidebarFile = root.toPath.resolve("sidebar.yml")
    if (!Files.exists(sidebarFile)) None
    else Some(Sidebar.load(Files.readAllLines(sidebarFile).asScala.mkString("\n")))

  lazy val templates: Seq[LoadedTemplate] = sideBarConfig.fold(loadAllFiles())(_.map(loadSidebarContent))

  lazy val mainPages: Seq[StaticPageNode] = templates.map(templateToPage)

  val docsPath = root.toPath.resolve("docs")

  lazy val allPages: Seq[StaticPageNode] = sideBarConfig.fold(mainPages){ sidebar =>
    def flattenPages(p: StaticPageNode): Set[Path] =
      Set(p.template.file.toPath) ++ p.getChildren.asScala.collect { case p: StaticPageNode => flattenPages(p) }.flatten

    val mainFiles = mainPages.toSet.flatMap(flattenPages)

    val allPaths =
      if !Files.exists(docsPath) then Nil
      else Files.walk(docsPath, FileVisitOption.FOLLOW_LINKS).iterator().asScala.toList

    val orphanedFiles = allPaths.filterNot { p =>
       def name = p.getFileName.toString
       def isMain = name == "index.html" || name == "index.md"
       mainFiles.contains(p) || (isMain && mainFiles.contains(p.getParent))
    }.filter { p =>
        val name = p.getFileName.toString
        name.endsWith(".md") || name.endsWith(".html")
    }

    val orphanedTemplates = orphanedFiles.flatMap(p => loadTemplate(p.toFile, isBlog = false))
    mainPages ++ orphanedTemplates.map(templateToPage)
  }

  private def isValidTemplate(file: File): Boolean =
    (file.isDirectory && !file.getName.startsWith("_")) ||
      file.getName.endsWith(".md") ||
      file.getName.endsWith(".html")


  private def loadTemplate(from: File, isBlog: Boolean = false): Option[LoadedTemplate] =
    if (!isValidTemplate(from)) None else
      try
        val topLevelFiles = if isBlog then Seq(from, new File(from, "_posts")) else Seq(from)
        val allFiles = topLevelFiles.filter(_.isDirectory).flatMap(_.listFiles())
        val (indexes, children) = allFiles.flatMap(loadTemplate(_)).partition(_.templateFile.isIndexPage())

        def loadIndexPage(): TemplateFile =
          val indexFiles = from.listFiles { file => file.getName == "index.md" || file.getName == "index.html" }
          indexes match
            case Nil => emptyTemplate(from, from.getName)
            case Seq(loadedTemplate) => loadedTemplate.templateFile.copy(file = from)
            case _ =>
              // TODO (https://github.com/lampepfl/scala3doc/issues/238): provide proper error handling
              val msg = s"ERROR: Multiple index pages for $from found in ${indexes.map(_.file)}"
              throw new java.lang.RuntimeException(msg)

        val templateFile = if (from.isDirectory) loadIndexPage() else loadTemplateFile(from)

        val processedChildren = if !isBlog then children else
          def dateFrom(p: LoadedTemplate): String =
            val pageSettings = p.templateFile.settings.get("page").collect{ case m: Map[String @unchecked, _] => m }
            pageSettings.flatMap(_.get("date").collect{ case s: String => s}).getOrElse("1900-01-01") // blogs without date are last
          children.sortBy(dateFrom).reverse

        val processedTemplate = // Set provided name as arg in page for `docs`
          if from.getParentFile.toPath == docsPath && templateFile.isIndexPage() then
            // TODO (https://github.com/lampepfl/scala3doc/issues/238): provide proper error handling
            if templateFile.title != "index" then println(s"[WARN] title in $from will be overriden")
            templateFile.copy(title = args.name)
          else templateFile

        Some(LoadedTemplate(processedTemplate, processedChildren.toList, from))
      catch
          case e: RuntimeException =>
            // TODO (https://github.com/lampepfl/scala3doc/issues/238): provide proper error handling
            e.printStackTrace()
            None

  def asContent(doctag: DocTag, dri: DRI) = new DocTagToContentConverter().buildContent(
    doctag,
    new DCI(Set(dri).asJava, ContentKind.Empty),
    sourceSets.asJava,
    JSet(),
    new PropertyContainer(JMap())
  )

  private def loadSidebarContent(entry: Sidebar): LoadedTemplate = entry match
    case Sidebar.Page(title, url) =>
      val isBlog = title == "Blog"
      val path = if isBlog then "blog" else
        if Files.exists(root.toPath.resolve(url)) then url
        else url.stripSuffix(".html") + ".md"

      val file = root.toPath.resolve(path).toFile
      val LoadedTemplate(template, children, _) = loadTemplate(file, isBlog).get // Add proper logging if file does not exisits
      LoadedTemplate(template.copy(settings = template.settings + ("title" -> title), file = file), children, file)

    case Sidebar.Category(title, nested) =>
      // Add support for index.html/index.md files!
      val fakeFile = new File(new File(root, "docs"), title)
      LoadedTemplate(emptyTemplate(fakeFile, title), nested.map(loadSidebarContent), fakeFile)

  private def loadAllFiles() =
    def dir(name: String)= List(new File(root, name)).filter(_.isDirectory)
    dir("docs").flatMap(_.listFiles()).flatMap(loadTemplate(_, isBlog = false))
      ++ dir("blog").flatMap(loadTemplate(_, isBlog = true))

  def driForLink(template: TemplateFile, link: String): Option[DRI] =
    val pathDri = Try {
      val path =
        if link.startsWith("/") then root.toPath.resolve(link.drop(1))
        else template.file.toPath.getParent().resolve(link)
      if Files.exists(path) then Some(driFor(path)) else None
    }.toOption.flatten
    pathDri.orElse(memberLinkResolver(link))

  def driFor(dest: Path): DRI = mkDRI(s"_.${root.toPath.relativize(dest)}")

  def relativePath(myTemplate: LoadedTemplate) = root.toPath.relativize(myTemplate.file.toPath)

  def templateToPage(myTemplate: LoadedTemplate): StaticPageNode =
    val dri = driFor(myTemplate.file.toPath)
    val content = new PartiallyRenderedContent(
      myTemplate,
      this,
      JList(),
      new DCI(Set(dri).asJava, ContentKind.Empty),
      sourceSets.toDisplay,
      JSet()
    )
    StaticPageNode(
      myTemplate.templateFile,
      myTemplate.templateFile.title,
      content,
      JSet(dri),
      JList(),
      (myTemplate.children.map(templateToPage)).asJava
    )

  val projectWideProperties =
    Seq("projectName" -> args.name) ++
      args.projectVersion.map("projectVersion" -> _)
