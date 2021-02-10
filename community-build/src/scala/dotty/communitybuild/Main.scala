package dotty.communitybuild

import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.Files
import scala.sys.process._


object Main:

  private def generateDocs(project: CommunityProject): Seq[Path] =
    val name = project.project
    try
      project.doc()
      val pathsOut = s"find community-projects/$name/ -name 'scaladoc.version'".!!
      pathsOut.linesIterator.map(Paths.get(_).getParent).toList
    catch
      case e: Exception =>
        e.printStackTrace()
        Nil

  def withProjects[T](names: Seq[String], opName: String)(op: CommunityProject => Unit): Unit =
    val missing = names.filterNot(projectMap.contains)
    if missing.nonEmpty then
      println(s"Missing projects: ${missing.mkString(", ")}. All projects: ${allProjects.mkString(", ")}")
      sys.exit(1)

    val failed = names.flatMap( o =>
      try
        op(projectMap(o))
        None
      catch case e: Throwable =>
        e.printStackTrace()
        Some(o)
    )

    if failed.nonEmpty then
      println(s"$opName failed for ${failed.mkString(", ")}")
      sys.exit(1)

  /** Allows running various commands on community build projects. */
  def main(args: Array[String]): Unit =
    args.toList match
      case "publish" :: names  =>
        withProjects(names, "Publishing")(_.publish())

      case "build" :: names =>
        withProjects(names, "Build")(_.build())

      case "doc" :: "all" :: destStr :: Nil =>
        val dest = Paths.get(destStr)
        Seq("rm", "-rf", destStr).!
        Files.createDirectory(dest)
        val (toRun, ignored) =
          allProjects.partition(_.docCommand != null)

        val paths = toRun.map { project =>
          val name = project.project
          val projectDest = dest.resolve(name)
          val projectRoot = Paths.get(s"community-projects/$name")
          println(s"generating docs for $name into $projectDest")
          val generatedDocs = generateDocs(project)
          if !Files.exists(projectDest) && generatedDocs.nonEmpty then
            Files.createDirectory(projectDest)

          val docsFiles = generatedDocs.map { docsPath =>
            val destFileName =
              docsPath.subpath(2, docsPath.getNameCount).toString.replace('/', '_')

            Seq("cp", "-r", docsPath.toString, projectDest.resolve(destFileName).toString).!
            destFileName
          }
          name -> docsFiles
        }

        val (failed, withDocs) = paths.partition{ case (_, paths) => paths.isEmpty }

        val indexFile = withDocs.map { case (name, paths) =>
          paths.map(p => s"""<a href="$name/$p/index.html">$p</a></br>\n""")
            .mkString(s"<h1>$name</h1>","\n", "\n")
        }.mkString("<html><body>\n", "\n", "\n</html></body>")

        Files.write(dest.resolve("index.html"), indexFile.getBytes)

        if ignored.nonEmpty then
          println(s"Ignored project without doc command: ${ignored.map(_.project)}")

        if failed.nonEmpty then
          println(s"Documentation not found for ${failed.map(_._1).mkString(", ")}")
          sys.exit(1)

      case "doc" :: names if names.nonEmpty =>
        val missing = names.filterNot(projectMap.contains)
        if missing.nonEmpty then
          println(s"Missing projects: ${missing.mkString(", ")}. All projects: ${allProjects.mkString(", ")}")
          sys.exit(1)

        val failed = names.filter{ p =>
          val docsRoots = generateDocs(projectMap(p))
          if docsRoots.nonEmpty then println(s"Docs for $p generated in $docsRoots")
          docsRoots.isEmpty
        }
        if failed.nonEmpty then
          println(s"Documentation not found for ${failed.mkString(", ")}")
          sys.exit(1)

      case args =>
        println("USAGE: <COMMAND> <PROJECT NAME>")
        println("COMMAND is one of: publish doc run")
        println("Available projects are:")
        allProjects.foreach { k =>
          println(s"\t${k.project}")
        }
        sys.exit(1)

