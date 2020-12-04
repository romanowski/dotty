package dotty.dokka

import org.jetbrains.dokka.plugability._
import org.jetbrains.dokka.transformers.sources._
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer
import org.jetbrains.dokka.transformers.pages.PageTransformer
import org.jetbrains.dokka.transformers.documentation.DocumentableToPageTranslator

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.{ DokkaConfiguration$DokkaSourceSet => DokkaSourceSet }
import org.jetbrains.dokka.model._
import org.jetbrains.dokka.model.doc._
import org.jetbrains.dokka.base.parsers._
import org.jetbrains.dokka.plugability.DokkaContext
import collection.JavaConverters._
import org.jetbrains.dokka.model.properties.PropertyContainer
import dotty.dokka.tasty.DokkaTastyInspector
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.pages._
import dotty.dokka.model.api._
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase

import dotty.dokka.site.NavigationCreator
import dotty.dokka.site.SitePagesCreator
import dotty.dokka.site.StaticSiteContext
import dotty.dokka.site.SiteResourceManager
import dotty.dokka.site.StaticSiteLocationProviderFactory

/** Main Dokka plugin for the doctool.
  *
  * Wires together classes responsible for consuming Tasty and generating
  * documentation.
  *
  * Most of the work of parsing Tasty is done by [[DokkaTastyInspector]].
  */
class DottyDokkaPlugin extends DokkaJavaPlugin:

  lazy val dokkaBase = plugin(classOf[DokkaBase])

  val provideMembers = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getSourceToDocumentableTranslator)
    .fromInstance(EmptyModuleProvider)
    .overrideExtension(dokkaBase.getPsiToDocumentableTranslator)
  )

  // Just turn off another translator since multiple overrides does not work
  val disableDescriptorTranslator = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getSourceToDocumentableTranslator)
    .fromRecipe { case ctx @ given DokkaContext => new ScalaModuleProvider }
    .overrideExtension(dokkaBase.getDescriptorToDocumentableTranslator)
    .name("disableDescriptorTranslator")
  )

  // Clean up empty module provided in disableDescriptorTranslator
  val cleanUpEmptyModules = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getPreMergeDocumentableTransformer)
      .fromInstance(_.asScala.filterNot(_.getName.isEmpty).asJava)
      .overrideExtension(dokkaBase.getModulesAndPackagesDocumentation)
  )

  val ourSignatureProvider = extend(
    _.extensionPoint(dokkaBase.getSignatureProvider)
      .fromRecipe{ case ctx @ given DokkaContext =>
        new ScalaSignatureProvider(ctx.single(dokkaBase.getCommentsToContentConverter))
      }.overrideExtension(dokkaBase.getKotlinSignatureProvider)
  )

  val scalaResourceInstaller = extend(
    _.extensionPoint(dokkaBase.getHtmlPreprocessors)
      .fromRecipe{ case ctx @ given DokkaContext => new ScalaResourceInstaller }
      .after(dokkaBase.getCustomResourceInstaller)
  )

  val scalaEmbeddedResourceAppender =  extend(
    _.extensionPoint(dokkaBase.getHtmlPreprocessors)
      .fromInstance(new ScalaEmbeddedResourceAppender())
      .after(dokkaBase.getCustomResourceInstaller)
      .name("scalaEmbeddedResourceAppender")
  )

  val scalaDocumentableToPageTranslator = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getDocumentableToPageTranslator)
      .fromRecipe { case ctx @ given DokkaContext =>
          new DocumentableToPageTranslator {
            override def invoke(module: DModule): ModulePageNode = ScalaPageCreator(
              ctx.single(dokkaBase.getCommentsToContentConverter),
              ctx.single(dokkaBase.getSignatureProvider)
            ).pageForModule(module)
          }
      }.overrideExtension(dokkaBase.getDocumentableToPageTranslator)
  )

  val packageHierarchyTransformer = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getPageTransformer)
      .fromRecipe(PackageHierarchyTransformer(_))
      .before(dokkaBase.getRootCreator)
      .overrideExtension(dokkaBase.getDefaultSamplesTransformer)
  )

  val inheritanceTransformer = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getDocumentableTransformer)
      .fromRecipe(InheritanceInformationTransformer(_))
      .name("inheritanceTransformer")
  )

  val ourRenderer = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getRenderer)
      .fromRecipe { case ctx @ given DokkaContext => new ScalaHtmlRenderer }
      .overrideExtension(dokkaBase.getHtmlRenderer)
  )

  val commentsToContentConverter = extend(
    _.extensionPoint(dokkaBase.getCommentsToContentConverter)
    .fromInstance(ScalaCommentToContentConverter)
    .overrideExtension(dokkaBase.getDocTagToContentConverter)
  )

  val implicitMembersExtensionTransformer = extend(
    _.extensionPoint(CoreExtensions.INSTANCE.getDocumentableTransformer)
      .fromRecipe { case ctx @ given DokkaContext =>
        new ImplicitMembersExtensionTransformer
      }.name("implicitMembersExtensionTransformer")
  )

  val customDocumentationProvider = extend(
    _.extensionPoint(dokkaBase.getHtmlPreprocessors)
      .fromRecipe{ case c @ given DokkaContext => new SitePagesCreator }
      .name("customDocumentationProvider")
      .ordered(
        before = Seq(
          dokkaBase.getNavigationPageInstaller,
          dokkaBase.getScriptsInstaller,
          dokkaBase.getStylesInstaller,
          dokkaBase.getPackageListCreator,
        ),
        after = Seq(dokkaBase.getRootCreator)
      )
  )

  val customNavigation = extend(
    _.extensionPoint(dokkaBase.getHtmlPreprocessors)
      .fromRecipe{ case c @ given DokkaContext => new NavigationCreator }
      .name("customNavigation")
      .ordered(
        before = Seq(
          dokkaBase.getScriptsInstaller,
          dokkaBase.getStylesInstaller,
        ),
        after = Seq(customDocumentationProvider.getValue)
      )
      .overrideExtension(dokkaBase.getNavigationPageInstaller)
  )

  val customDocumentationResources = extend(
    _.extensionPoint(dokkaBase.getHtmlPreprocessors)
    .fromRecipe{ case c @ given DokkaContext => new SiteResourceManager }
      .name("customDocumentationResources")
      .after(
        scalaEmbeddedResourceAppender.getValue,
        customDocumentationProvider.getValue
      )
  )

  val locationProvider = extend(
    _.extensionPoint(dokkaBase.getLocationProviderFactory)
      .fromRecipe { case c @ given DokkaContext => new StaticSiteLocationProviderFactory }
      .overrideExtension(dokkaBase.getLocationProvider)
  )

// TODO (https://github.com/lampepfl/scala3doc/issues/232): remove once problem is fixed in Dokka
extension [T]  (builder: ExtensionBuilder[T]):
  def ordered(before: Seq[Extension[_, _, _]], after: Seq[Extension[_, _, _]]): ExtensionBuilder[T] =
    val byDsl = new OrderingKind.ByDsl(dsl => {
      dsl.after(after:_*)
      dsl.before(before:_*)
      kotlin.Unit.INSTANCE // TODO why U does not work here?
    })
    // Does not compile but compiles in scala 2
    // ExtensionBuilder.copy$default(builder, null, null, null, byDsl, null, null, 55, null)
    val m = classOf[ExtensionBuilder[_]].getDeclaredMethods().find(_.getName == "copy$default").get
    m.setAccessible(true)
    // All nulls and 55 is taken from Kotlin bytecode and represent how defaut parameter are represented in Kotlin
    // Defaut arguments are encoded by null and mapping that 55 represent whic arguments are actually provided
    m.invoke(null, builder, null, null, null, byDsl, null, null, 55, null).asInstanceOf[ExtensionBuilder[T]]


  def before(exts: Extension[_, _, _]*):  ExtensionBuilder[T] = ordered(exts, Nil)

  def after(exts: Extension[_, _, _]*):  ExtensionBuilder[T] = ordered(Nil, exts)
