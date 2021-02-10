package dotty.tools.scaladoc

import java.util.ServiceLoader
import java.io.File
import java.util.jar._
import collection.JavaConverters._
import collection.immutable.ArraySeq

import java.nio.file.Files

import dotty.tools.dotc.config.Settings._
import dotty.tools.dotc.config.CommonScalaSettings
import dotty.tools.scaladoc.Scaladoc._

class ScaladocArgs extends SettingGroup with CommonScalaSettings:
  val unsupportedSettings = Seq(
    // Options that we like to support
    bootclasspath, extdirs, javabootclasspath, encoding, usejavacp,
    // Needed for plugin architecture
    plugin,disable,require, pluginsDir, pluginOptions,
    // we need support for sourcepath and sourceroot
    sourcepath, sourceroot
  )

  val sourceLinks: Setting[List[String]] =
    MultiStringSetting("-source-links", "sources", SourceLinks.usage)

  val syntax: Setting[String] =
    StringSetting("-comment-syntax", "syntax", "Syntax of the comment used", "")

  val revision: Setting[String] =
    StringSetting("-revision", "revision", "Revision (branch or ref) used to build project project", "")

  val externalDocumentationMappings: Setting[List[String]] =
    MultiStringSetting("-external-mappings", "external-mappings",
      "Mapping between regexes matching classpath entries and external documentation. " +
        "'regex::[scaladoc|scaladoc|javadoc]::path' syntax is used")

  val socialLinks: Setting[List[String]] =
    MultiStringSetting("-social-links", "social-links",
      "Links to social sites. '[github|twitter|gitter|discord]::link' syntax is used. " +
        "'custom::link::white_icon_name::black_icon_name' is also allowed, in this case icons must be present in 'images/'' directory.")

  val deprecatedSkipPackages: Setting[List[String]] =
    MultiStringSetting("-skip-packages", "packages", "Deprecated, please use `-skip-by-id` or `-skip-by-regex`")

  val skipById: Setting[List[String]] =
    MultiStringSetting("-skip-by-id", "package or class identifier", "Identifiers of packages or top-level classes to skip when generating documentation")

  val skipByRegex: Setting[List[String]] =
    MultiStringSetting("-skip-by-regex", "regex", "Regexes that match fully qualified names of packages or top-level classes to skip when generating documentation")

  val docRootContent: Setting[String] =
    StringSetting("-doc-root-content", "path", "The file from which the root package documentation should be imported.", "")

  def scaladocSpecificSettings: Set[Setting[_]] =
    Set(sourceLinks, syntax, revision, externalDocumentationMappings, socialLinks, skipById, skipByRegex, deprecatedSkipPackages, docRootContent)

object ScaladocArgs:
  def extract(args: List[String], rootCtx: CompilerContext):(Scaladoc.Args, CompilerContext) =
    val inst = new ScaladocArgs
    import inst._
    val initialSummary =
      ArgsSummary(defaultState, args, errors = Nil, warnings = Nil)
    val summary =
      processArguments(initialSummary, processAll = true, skipped = Nil)
    val newContext = rootCtx.fresh

    extension[T](arg: Setting[T])
      def get = arg.valueIn(summary.sstate)
      def withDefault(default: => T) =
        if arg.get == arg.default then default else arg.get
      def nonDefault =
        if arg.get == arg.default then None else Some(arg.get)

    def setInGlobal[T](s: Setting[T]) =
      s.nonDefault.foreach { newValue =>
        newContext.settings.allSettings.find(_ == s).fold(
          report.warning(s"Unable to set ${s.name} in global context")
        )(s => newContext.setSetting(s.asInstanceOf[Setting[T]], newValue))
      }

    allSettings.filterNot(scaladocSpecificSettings.contains).foreach(setInGlobal)

    given CompilerContext = newContext
    summary.warnings.foreach(report.warning(_))
    summary.errors.foreach(report.error(_))

    def parseTastyRoots(roots: String) =
      roots.split(File.pathSeparatorChar).toList.map(new File(_))

    val inFiles = summary.arguments.map(File(_)).filter(_.getName != "___fake___.scala")
    val (existing, nonExisting) = inFiles.partition(_.exists)

    if nonExisting.nonEmpty then report.warning(
      s"scaladoc will ignore following non-existent paths: ${nonExisting.mkString(", ")}"
    )

    val (dirs, files) = existing.partition(_.isDirectory)
    val (validFiles, other) = files.partition(f =>
      f.getName.endsWith(".tasty") || f.getName.endsWith(".jar")
    )

    if other.nonEmpty then report.warning(
      s"scaladoc suports only .tasty and .jar files, following files will be ignored: ${other.mkString(", ")}"
    )

    def defaultDest(): File =
      report.error("Destination is not provided, please provide '-d' parameter pointing to directory where docs should be created")
      File("output")

    val parseSyntax = syntax.nonDefault.fold(CommentSyntax.default){ str =>
      CommentSyntax.parse(str).getOrElse{
        report.error(s"unrecognized value for -syntax option: $str")
        CommentSyntax.default
      }
    }
    val externalMappings =
      externalDocumentationMappings.get.flatMap( s =>
          ExternalDocLink.parse(s).fold(left => {
            report.warning(left)
            None
          }, right => Some(right)
        )
      )

    val socialLinksParsed =
      socialLinks.get.flatMap { s =>
        SocialLinks.parse(s).fold(left => {
          report.warning(left)
          None
        },right => Some(right))
      }

    unsupportedSettings.filter(s => s.get != s.default).foreach { s =>
      report.warning(s"Setting ${s.name} is currently not supported.")
    }
    val destFile = outputDir.nonDefault.fold(defaultDest())(_.file)
    val printableProjectName = projectName.nonDefault.fold("")("for " + _ )
    report.inform(
      s"Generating documenation $printableProjectName in $destFile")

    if deprecatedSkipPackages.get.nonEmpty then report.warning(deprecatedSkipPackages.description)

    val docArgs = Args(
      projectName.withDefault("root"),
      dirs,
      validFiles,
      classpath.get,
      destFile,
      siteRoot.nonDefault,
      projectVersion.nonDefault,
      projectLogo.nonDefault,
      parseSyntax,
      sourceLinks.get,
      revision.nonDefault,
      externalMappings,
      socialLinksParsed,
      skipById.get ++ deprecatedSkipPackages.get,
      skipByRegex.get,
      docRootContent.nonDefault
    )
    (docArgs, newContext)
