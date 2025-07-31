package io.github.nafg.millbundler

import io.github.nafg.millbundler.testcommon.BaseSuite

class WebpackSuite extends BaseSuite {
  test("Webpack - simple") {
    object build extends BaseBuild {
      object test extends BaseTestModule with ScalaJSWebpackModule.Test
    }

    checkTestResults(build)(build.test, "webpack-simple")
  }
}
