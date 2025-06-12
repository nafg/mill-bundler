import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.1`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion
import io.kipp.mill.ci.release.CiReleaseModule
import mill._
import mill.scalalib._
import mill.scalalib.api.JvmWorkerUtil.scalaNativeBinaryVersion
import mill.scalalib.publish._
import mill.scalalib.scalafmt._

def millVersionFile = T.source(PathRef(os.pwd / ".mill-version"))

def millVersion = T {
  os.read(millVersionFile().path).trim
}

trait CommonModule
    extends ScalaModule
    with CiReleaseModule
    with ScalafmtModule {
  override def scalaVersion = "2.13.12"

  override def publishVersion: T[String] = T {
    VcsVersion
      .vcsState()
      .format(
        dirtyHashDigits = 0,
        untaggedSuffix = "-SNAPSHOT"
      )
  }

  override def artifactSuffix =
    "_mill" + scalaNativeBinaryVersion(millVersion()) +
      super.artifactSuffix()

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

  override def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalajslib:${millVersion()}"
  )
}

object jsdeps extends CommonModule {
  object test extends MillIntegrationTestModule {
    override def millTestVersion = millVersion()
    override def pluginsUnderTest = Seq(jsdeps)
  }
}

//noinspection ScalaUnusedSymbol
object millbundler extends CommonModule {
  override def moduleDeps = Seq(jsdeps)
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::geny:1.0.0"
  )

  object test extends MillIntegrationTestModule {
    override def millTestVersion = millVersion()
    override def pluginsUnderTest = Seq(millbundler)
  }
}
