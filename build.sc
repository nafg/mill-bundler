import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.1`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.main.BuildInfo
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalalib.scalafmt._

val millVersion = "1.0.1"

trait BaseScalaModule extends ScalaModule {
  override def scalaVersion = "3.7.0"
  override def zincReportCachedProblems = true
}

trait BasePublishModule
    extends BaseScalaModule
    with SonatypeCentralPublishModule
    with ScalafmtModule {
  override def scalaVersion = "3.7.0"

  override def publishVersion: T[String] = T {
    VcsVersion
      .vcsState()
      .format(
        dirtyHashDigits = 0,
        untaggedSuffix = "-SNAPSHOT"
      )
  }

  override def platformSuffix =
    "_mill" + BuildInfo.millBinPlatform + super.platformSuffix()

  override def pomSettings = PomSettings(
    description = "Bundler for Mill",
    organization = "io.github.nafg.millbundler",
    url = "https://github.com/nafg/mill-bundler",
    licenses = Seq(License.`Apache-2.0`),
    versionControl =
      VersionControl.github(owner = "nafg", repo = "mill-bundler"),
    developers =
      Seq(Developer("nafg", "Naftoli Gugenheim", "https://github.com/nafg"))
  )

  override def scalacOptions = Seq("-Ywarn-unused", "-deprecation")

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-libs-scalajslib:$millVersion"
  )
}

object test_common extends BaseScalaModule {
  override def moduleDeps = Seq(jsdeps)

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-testkit:$millVersion",
    ivy"org.scalameta::munit::1.1.1"
  )
}

object jsdeps extends BasePublishModule {
  object test extends ScalaTests with TestModule.Munit {
    override def moduleDeps = super.moduleDeps :+ test_common
  }
}

//noinspection ScalaUnusedSymbol
object millbundler extends BasePublishModule {
  override def moduleDeps = Seq(jsdeps)
  override def ivyDeps = Agg(ivy"com.lihaoyi::geny:1.1.1")

  object test extends ScalaTests with TestModule.Munit {
    override def moduleDeps = super.moduleDeps :+ test_common
    override def testParallelism = true
  }
}
