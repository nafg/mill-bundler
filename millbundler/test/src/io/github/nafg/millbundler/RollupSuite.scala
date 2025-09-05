package io.github.nafg.millbundler

import io.github.nafg.millbundler.testcommon.BaseSuite

import mill.api.Discover
import mill.scalajslib.api.ModuleKind
import mill.util.TokenReaders.*

class RollupSuite extends BaseSuite:

  test("Rollup") {
    object build extends BaseBuild:
      object test extends BaseTestModule with ScalaJSRollupModule.Test:
        override def rollupPlugins =
          super.rollupPlugins() :+
            ScalaJSRollupModule.Plugin.core("json")
        override def rollupOutputName = Some("RollupTest")

        override def moduleKind = ModuleKind.ESModule

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[test.type]
    end build

    checkTestResults(build.test, "rollup")
  }

end RollupSuite
