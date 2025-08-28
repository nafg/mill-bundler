package io.github.nafg.millbundler.jsdeps

import io.github.nafg.millbundler.testcommon.BaseSuite
import mill.api.Discover
import mill.util.TokenReaders.*

class JsDepsSuite extends BaseSuite {
  test("JsDeps") {
    object build extends BaseBuild with ScalaJSNpmModule {
      object test extends BaseTestModule with ScalaJSNpmModule.Test {
        lazy val millDiscover = Discover[this.type]
      }

      lazy val millDiscover = Discover[test.type]
    }

    checkTestResults(build.test, "simple")
  }
}
