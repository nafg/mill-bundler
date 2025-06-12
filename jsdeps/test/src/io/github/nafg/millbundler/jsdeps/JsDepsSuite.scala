package io.github.nafg.millbundler.jsdeps

import io.github.nafg.millbundler.testcommon.BaseSuite

class JsDepsSuite extends BaseSuite {
  test("JsDeps") {
    object build extends BaseBuild with ScalaJSNpmModule {
      object test extends BaseTestModule with ScalaJSNpmModule.Test
    }

    checkTestResults(build.test, "simple")
  }
}
