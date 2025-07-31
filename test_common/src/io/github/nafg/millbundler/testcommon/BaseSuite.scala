package io.github.nafg.millbundler.testcommon

import scala.concurrent.duration.DurationInt

import io.github.nafg.millbundler.jsdeps.ScalaJSDepsModule

import mill.*
import mill.api.Discover
import mill.javalib.testrunner.TestResult
import mill.scalajslib.api.ModuleKind
import mill.scalalib.{DepSyntax, TestModule}
import mill.testkit.{TestRootModule, UnitTester}

class BaseSuite extends munit.FunSuite {
  override def munitTimeout = 4.minute

  private val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  protected def checkTestResults(testResults: Seq[TestResult]): Unit = {
    assert(testResults.nonEmpty, "No tests found")
    assert(
      testResults.forall(_.status == "Success"),
      "Some tests failed"
    )
  }

  protected def checkTestResults(
      base: BaseBuild
  )(
      test: base.BaseTestModule,
      name: String
  ): Unit =
    UnitTester(test, resourceFolder / name)
      .scoped { tester =>
        tester(test.testForked()) match {
          case Left(f)                                       => f.throwException
          case Right(UnitTester.Result((_, testResults), _)) =>
            checkTestResults(testResults)
        }
      }

  abstract class BaseBuild extends TestRootModule with ScalaJSDepsModule {
    override def scalaVersion = "2.13.16"
    override def scalaJSVersion = "1.19.0"

    lazy val millDiscover: Discover = Discover[this.type]

    abstract class BaseTestModule
        extends TestRootModule
        with ScalaJSTests
        with TestModule.Munit {
      override def moduleKind = ModuleKind.CommonJSModule
      override def mvnDeps =
        super.mvnDeps() ++ Seq(
          mvn"org.scalameta::munit::1.1.1",
          mvn"io.github.nafg.scalajs-facades::react-phone-number-input_3::0.16.0"
        )

      lazy val millDiscover: Discover = Discover[this.type]
    }
  }
}
