import $file.plugins

import io.github.nafg.millbundler.jsdeps.ScalaJSDepsModule
import io.github.nafg.millbundler.ScalaJSRollupModule
import mill.scalajslib.api.ModuleKind
import mill.scalalib.{DepSyntax, TestModule}
import mill.{Agg, T}

object main extends ScalaJSDepsModule {
  override def scalaVersion = "2.13.12"
  override def scalaJSVersion = "1.10.1"

  override def ivyDeps =
    Agg(
      ivy"io.github.nafg.scalajs-facades::react-phone-number-input_3::0.16.0"
    )

  object test
      extends ScalaJSTests
      with ScalaJSRollupModule.Test
      with TestModule.Munit {

    override def moduleKind = ModuleKind.CommonJSModule

    override def ivyDeps =
      super.ivyDeps() ++ Agg(ivy"org.scalameta::munit::0.7.29")

    override def rollupPlugins =
      super.rollupPlugins() :+
        ScalaJSRollupModule.Plugin.core("json")
  }
}

def verify = T {
  val logger = T.ctx().log
  val (_, testResults) = main.test.test()()
  assert(testResults.nonEmpty, "No tests found")
}
