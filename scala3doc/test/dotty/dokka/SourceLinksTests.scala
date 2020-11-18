package dotty.dokka

import java.nio.file._
import org.junit.Assert._
import org.junit.Test

class SourceLinkTest:

  @Test
  def testBasicFailures() =
    def testFailure(template: String, messagePart: String) =
      val res = SourceLink.parse(template, None)
      assertTrue(s"Expected failure containing $messagePart: $res", res.left.exists(_.contains(messagePart)))

      val resWithVersion = SourceLink.parse(template, Some("develop"))
      assertEquals(res, resWithVersion)

    testFailure("ala://ma/kota", "known provider")
    testFailure("ala=ala=ala://ma/kota", "known provider")
    testFailure("ala=ala=ala", "subpath")
    testFailure("""{{ ala "ala"}}""", "parse")
    testFailure("""€{TPL_NAME}""", "scaladoc")


  @Test
  def testProperTemplates() =
    def test(template: String) =
      val res = try SourceLink.parse(template, Some("develop")) catch
        case e: Exception => throw RuntimeException(s"When testing $template", e)
      assertTrue(s"Bad template: $template", res.isRight)


    Seq(
      """https://github.com/organization/repo/{{ operation | replace: "view", "blob" }}/$revision/{{ path }}{{ line | prepend: "L#"}}""",
      "github://lampepfl/dotty",
      "gitlab://lampepfl/dotty",
      "https://github.com/scala/scala/blob/2.13.x€{FILE_PATH_EXT}#€{FILE_LINE}"
    ).foreach{ template =>
      test(template)
      test(s"docs/dotty=$template")
    }


  @Test
  def testSourceProviderWithoutRevision() =
    Seq("github", "gitlab").foreach { provider =>
      val template = s"$provider://ala/ma"
      val res = SourceLink.parse(template, None)
      assertTrue(s"Expected failure containing missing revision: $res", res.left.exists(_.contains("revision")))

      Seq(s"$provider://ala/ma/", s"$provider://ala", s"$provider://ala/ma/develop").foreach { template =>
        val res = SourceLink.parse(template, Some("develop"))
        assertTrue(s"Expected failure syntax info: $res", res.left.exists(_.contains("syntax")))
      }

    }

class SourceLinksTest:
  // TODO (https://github.com/lampepfl/scala3doc/issues/240): configure source root
  val projectRoot = Paths.get("").toAbsolutePath()

  val edit: Operation = "edit" // union types need explicit singletons

  type Args = String | (String, Operation) | (String, Int) | (String, Int, Operation)

  private def testLink(config: Seq[String], revision: Option[String])(cases: (Args, String | None.type)*): Unit =
    val links = SourceLinks.load(config, revision, projectRoot)
    cases.foreach { case (args, expected) =>
      val res = args match
        case path: String => links.pathTo(projectRoot.resolve(path))
        case (path: String, line: Int) => links.pathTo(projectRoot.resolve(path), line = Some(line))
        case (path: String, operation: Operation) => links.pathTo(projectRoot.resolve(path), operation = operation)
        case (path: String, line: Int, operation: Operation) => links.pathTo(projectRoot.resolve(path), operation = operation, line = Some(line))
        case _ => ??? // needed due to handling of singleton types inside union types

      val expectedRes = expected match
        case s: String => Some(s)
        case None => None

      assertEquals(s"For path $args", expectedRes, res)
    }

  @Test
  def testBasicPaths =
    testLink(Seq("github://lampepfl/dotty"), Some("develop"))(
      "project/Build.scala" -> "https://github.com/lampepfl/dotty/blob/develop/project/Build.scala#L",
      ("project/Build.scala", 54) -> "https://github.com/lampepfl/dotty/blob/develop/project/Build.scala#L54",
      ("project/Build.scala", edit) -> "https://github.com/lampepfl/dotty/edit/develop/project/Build.scala#L",
      ("project/Build.scala", 54, edit) -> "https://github.com/lampepfl/dotty/edit/develop/project/Build.scala#L54",
    )

    testLink(Seq("gitlab://lampepfl/dotty"), Some("develop"))(
      "project/Build.scala" -> "https://gitlab.com/lampepfl/dotty/-/blob/develop/project/Build.scala#L",
      ("project/Build.scala", 54) -> "https://gitlab.com/lampepfl/dotty/-/blob/develop/project/Build.scala#L54",
      ("project/Build.scala", edit) -> "https://gitlab.com/lampepfl/dotty/-/edit/develop/project/Build.scala#L",
      ("project/Build.scala", 54, edit) -> "https://gitlab.com/lampepfl/dotty/-/edit/develop/project/Build.scala#L54",
    )

    testLink(Seq("/{{operation}}/{{path}}#{{line}}"), Some("develop"))(
      "project/Build.scala" -> "/view/project/Build.scala#",
      ("project/Build.scala", 54) -> "/view/project/Build.scala#54",
      ("project/Build.scala", edit) -> "/edit/project/Build.scala#",
      ("project/Build.scala", 54, edit) -> "/edit/project/Build.scala#54",
    )

    testLink(Seq("https://github.com/scala/scala/blob/2.13.x/€{FILE_PATH_EXT}#L€{FILE_LINE}"), Some("develop"))(
      "project/Build.scala" -> "https://github.com/scala/scala/blob/2.13.x/project/Build.scala#L",
      ("project/Build.scala", 54) -> "https://github.com/scala/scala/blob/2.13.x/project/Build.scala#L54",
      ("project/Build.scala", edit) -> "https://github.com/scala/scala/blob/2.13.x/project/Build.scala#L",
      ("project/Build.scala", 54, edit) -> "https://github.com/scala/scala/blob/2.13.x/project/Build.scala#L54",
    )

  @Test
  def testBasicPrefixedPaths =
    testLink(Seq("src=gitlab://lampepfl/dotty"), Some("develop"))(
      "src/lib/core.scala" -> "https://gitlab.com/lampepfl/dotty/-/blob/develop/src/lib/core.scala#L",
      ("src/lib/core.scala", 33, edit) -> "https://gitlab.com/lampepfl/dotty/-/edit/develop/src/lib/core.scala#L33",
      ("src/lib/core.scala", 33, edit) -> "https://gitlab.com/lampepfl/dotty/-/edit/develop/src/lib/core.scala#L33",
      "build.sbt" -> None
    )


  @Test
  def prefixedPaths =
    testLink(Seq(
     "src/generated=/{{operation}}/{{path}}#{{line}}",
      "src=gitlab://lampepfl/dotty",
      "github://lampepfl/dotty"
      ), Some("develop"))(
      ("project/Build.scala", 54, edit) -> "https://github.com/lampepfl/dotty/edit/develop/project/Build.scala#L54",
      ("src/lib/core.scala", 33, edit) -> "https://gitlab.com/lampepfl/dotty/-/edit/develop/src/lib/core.scala#L33",
      ("src/generated.scala", 33, edit) -> "https://gitlab.com/lampepfl/dotty/-/edit/develop/src/generated.scala#L33",
      ("src/generated/template.scala", 1, edit) -> "/edit/src/generated/template.scala#1"
    )