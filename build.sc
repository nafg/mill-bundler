import $ivy.`io.chris-kipp::mill-ci-release::0.1.3`

import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.publish._
import mill.scalalib.api.ZincWorkerUtil.scalaNativeBinaryVersion
import io.kipp.mill.ci.release.CiReleaseModule


//noinspection ScalaWeakerAccess
trait CommonModule extends ScalaModule with CiReleaseModule with ScalafmtModule {
  override def scalaVersion = "2.13.10"

  def millVersionFile = T.source(PathRef(os.pwd / ".mill-version"))

  def millVersion = T {
    os.read(millVersionFile().path).trim
  }

  override def artifactSuffix =
    "_mill" + scalaNativeBinaryVersion(millVersion()) +
      super.artifactSuffix()

  override def pomSettings = PomSettings(
    description = "Bundler for Mill",
    organization = "io.github.nafg.millbundler",
    url = "https://github.com/nafg/mill-bundler",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github(owner = "nafg", repo = "mill-bundler"),
    developers = Seq(Developer("nafg", "Naftoli Gugenheim", "https://github.com/nafg"))
  )

  override def scalacOptions = Seq("-Ywarn-unused", "-deprecation")

  override def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalajslib:${millVersion()}"
  )
}

object jsdeps extends CommonModule

//noinspection ScalaUnusedSymbol
object millbundler extends CommonModule {
  override def moduleDeps = Seq(jsdeps)
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::geny:0.6.10"
  )
}
