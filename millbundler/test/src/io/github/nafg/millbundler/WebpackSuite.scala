package io.github.nafg.millbundler

import io.github.nafg.millbundler.testcommon.BaseSuite
import mill.api.Discover
import mill.util.TokenReaders.*

class WebpackSuite extends BaseSuite {
  test("Webpack - simple") {
    object build extends BaseBuild {
      object test extends BaseTestModule with ScalaJSWebpackModule.Test {
        lazy val millDiscover = Discover[this.type]
      }

      lazy val millDiscover = Discover[test.type]
    }

    checkTestResults(build.test, "webpack-simple")
  }
}
