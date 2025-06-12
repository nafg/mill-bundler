package io.github.nafg.millbundler

import io.github.nafg.millbundler.testcommon.BaseSuite

class RollupSuite extends BaseSuite {
  test("Rollup") {
    object build extends BaseBuild {
      object test extends BaseTestModule with ScalaJSRollupModule.Test {
        override def rollupPlugins =
          super.rollupPlugins() :+
            ScalaJSRollupModule.Plugin.core("json")
      }
    }

    checkTestResults(build.test, "rollup")
  }
}
