import $exec.plugins

import io.github.nafg.millbundler.jsdeps.ScalaJSDepsModule
import io.github.nafg.millbundler.jsdeps.JsDeps
import io.github.nafg.millbundler.ScalaJSWebpackModule
import mill.scalajslib.api.JsEnvConfig
import mill.scalajslib.api.ModuleKind
import mill.scalalib.{DepSyntax, TestModule}
import mill.define.Command
import mill.testrunner.TestRunner

object main extends ScalaJSDepsModule {
  override def scalaVersion = "2.13.10"
  override def scalaJSVersion = "1.10.1"

  object test
    extends Tests
      with ScalaJSWebpackModule.Test
      with TestModule.Munit {

    override def moduleKind = ModuleKind.CommonJSModule

    override def ivyDeps =
      super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::0.7.29")

    override def jsEnvConfig = T(JsEnvConfig.NodeJs())
    override def jsDeps = super.jsDeps() ++ JsDeps("jsdom" -> "20.0.3")
  }
}

def verify = T {
  val logger = T.ctx().log
  val (_, testResults) = main.test.test()()
  assert(testResults.nonEmpty, "No tests found")
}
