package dotty.dokka

import org.jetbrains.dokka._
import org.jetbrains.dokka.utilities._
import org.jetbrains.dokka.plugability._
import java.util.ServiceLoader
import java.io.File
import java.util.jar._
import collection.JavaConverters._
import collection.immutable.ArraySeq
import java.util.{List => JList}

import scala.tasty.Reflection
import scala.tasty.inspector.TastyInspector
import java.nio.file.Files

import dotty.tools.dotc.config.Settings._

class Scala3Args extends SettingGroup:
  val tastyRoots: Setting[String] =
    StringSetting("--tastyRoots", "tastyRoots", "Roots where tools should look for tasty files", "", aliases = List("-t"))
  val dest: Setting[String] =
    StringSetting("--dest", "dest", "Output to generate documentation to", "", aliases = List("-d"))
  val classpath: Setting[String] =
    StringSetting("--classpath", "tastyRoots", "Classpath to load dependencies from", System.getProperty("java.class.path"), aliases = List("--cp", "-c"))
  val name: Setting[String] =
    StringSetting("--name", "name", "Name of module in generated documentation", "", aliases = List("-n"))
  val docsRoot: Setting[String] =
    StringSetting("--docs", "docs", "Root of project docs", "", aliases = List("-p"))
  val sourceLinks: Setting[List[String]] =
    MultiStringSetting("--sources", "sources", "Links to source files provided in convention: local_directory=remote_directory#line_suffix")
  val projectTitle: Setting[String] =
    StringSetting("--projectTitle", "projectTitle", "Title of the project used in documentation", "")
  val projectVersion: Setting[String] =
    StringSetting("--projectVersion", "projectVersion", "Version of the project used in documentation", "")
  val projectLogo: Setting[String] =
    StringSetting("--projectLogo", "projectLogo", "Relative path to logo of the project", "")
  val syntax: Setting[String] =
    StringSetting("--syntax", "syntax", "Syntax of the comment used", "")


  def extract(args: List[String], err: String => Nothing) =
      val initialSummary = ArgsSummary(defaultState, args, errors = Nil, warnings = Nil)
      val res = processArguments(initialSummary, processAll = true, skipped = Nil)
      if res.errors.nonEmpty then err(s"Unable to parse arguments:\n ${res.errors.mkString("\n")}")

      val parsedSyntax = syntax.valueIn(res.sstate) match
        case "" => None
        case other =>
          Args.CommentSyntax.fromString(other) match
            case None =>
              err(s"unrecognized value for --syntax option: $other")
            case some => some

      def parseOptionalArg(arg: Setting[String]) = arg.valueIn(res.sstate) match
        case "" => None
        case value => Some(value)

      def requiredArg(arg: Setting[String]) = arg.valueIn(res.sstate) match
       case "" => err(s"Missing required setting ${arg.name}")
       case value => value

      Args(
        requiredArg(name),
        requiredArg(tastyRoots).split(File.pathSeparatorChar).toList.map(new File(_)),
        classpath.valueIn(res.sstate),
        new File(requiredArg(dest)),
        parseOptionalArg(docsRoot),
        projectVersion.valueIn(res.sstate),
        parseOptionalArg(projectTitle),
        parseOptionalArg(projectLogo),
        parsedSyntax,
        sourceLinks.valueIn(res.sstate)
      )

case class Args(
  name: String,
  tastyRoots: Seq[File],
  classpath: String,
  output: File,
  docsRoot: Option[String],
  projectVersion: String,
  projectTitle: Option[String],
  projectLogo: Option[String],
  defaultSyntax: Option[Args.CommentSyntax],
  sourceLinks: List[String]
)

object Args:
  enum CommentSyntax:
    case Wiki
    case Markdown

  object CommentSyntax:
    def fromString(str: String): Option[CommentSyntax] =
      str match
        case "wiki" => Some(Wiki)
        case "markdown" => Some(Markdown)
        case _ => None
end Args

import dotty.tools.dotc.core.Contexts.{Context => DottyContext}
trait BaseDocConfiguration:
  val args: Args
  val tastyFiles: List[String]

enum DocConfiguration extends BaseDocConfiguration:
  case Standalone(args: Args, tastyFiles: List[String])
  case Sbt(args: Args, tastyFiles: List[String], rootCtx: DottyContext)

/** Main class for the doctool.
  *
  * The `main` method is mostly responsible just for parsing arguments and
  * configuring Dokka. After that, we hand control to Dokka.
  *
  * Other important classes:
  *
  * - [](package.DottyDokkaPlugin) is our class that Dokka calls back and which
  *   actually generates the documentation.
  * - [](package.DottyDokkaConfig) is our config for Dokka.
  */
object Main:
  def main(args: Array[String]): Unit =
    try
      val parsedArgs = Scala3Args().extract(args.toList, sys.error)
      val (jars, dirs) = parsedArgs.tastyRoots.partition(_.isFile)
      val extracted = jars.filter(_.exists()).map { jarFile =>
          val tempFile = Files.createTempDirectory("jar-unzipped").toFile
          IO.unzip(jarFile, tempFile)
          tempFile
      }

      try
        def listTastyFiles(f: File): Seq[String] =
          val (files, dirs) = f.listFiles().partition(_.isFile)
          ArraySeq.unsafeWrapArray(
            files.filter(_.getName.endsWith(".tasty")).map(_.toString) ++ dirs.flatMap(listTastyFiles)
          )
        val tastyFiles = (dirs ++ extracted).flatMap(listTastyFiles).toList

        val config = DocConfiguration.Standalone(parsedArgs, tastyFiles)

        if (parsedArgs.output.exists()) IO.delete(parsedArgs.output)

        // TODO #20 pass options, classpath etc.
        new DokkaGenerator(new DottyDokkaConfig(config), DokkaConsoleLogger.INSTANCE).generate()

        println("Done")


      finally
        extracted.foreach(IO.delete)
      // Sometimes jvm is hanging, so we want to be sure that we force shout down the jvm
      sys.exit(0)
    catch
      case a: Exception =>
        a.printStackTrace()
        // Sometimes jvm is hanging, so we want to be sure that we force shout down the jvm
        sys.exit(1)
