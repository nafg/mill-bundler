import $file.plugins

import io.github.nafg.millbundler.jsdeps.ScalaJSNpmModule
import mill.scalajslib.api.ModuleKind
import mill.scalalib.{DepSyntax, TestModule}
import mill.scalajslib.api.JsEnvConfig

object main extends ScalaJSNpmModule {
  override def scalaVersion = "2.13.10"
  override def scalaJSVersion = "1.10.1"

  override def ivyDeps =
    Agg(
      ivy"io.github.nafg.scalajs-facades::react-phone-number-input_3::0.16.0"
    )

  object test extends Tests with ScalaJSNpmModule.Test with TestModule.Munit {

    override def moduleKind = ModuleKind.CommonJSModule

    override def ivyDeps =
      super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::0.7.29")
  }
}

def verify = T {
  val logger = T.ctx().log
  val (_, testResults) = main.test.test()()
  assert(testResults.nonEmpty, "No tests found")
}
