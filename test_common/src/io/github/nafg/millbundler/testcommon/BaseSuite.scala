package io.github.nafg.millbundler.testcommon

import io.github.nafg.millbundler.jsdeps.ScalaJSDepsModule

import mill.Agg
import mill.scalajslib.api.ModuleKind
import mill.scalalib.{DepSyntax, TestModule}
import mill.testkit.{TestBaseModule, UnitTester}
import mill.testrunner.TestResult

class BaseSuite extends munit.FunSuite {
  private val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  protected def checkTestResults(testResults: Seq[TestResult]): Unit = {
    assert(testResults.nonEmpty, "No tests found")
    assert(
      testResults.forall(_.status == "Success"),
      "Some tests failed"
    )
  }

  protected def checkTestResults(
      test: BaseBuild#BaseTestModule,
      name: String
  ): Unit =
    UnitTester(test, resourceFolder / name)
      .scoped { tester =>
        tester(test.test()) match {
          case Left(f) => throw f
          case Right(UnitTester.Result((_, testResults), _)) =>
            checkTestResults(testResults)
        }
      }

  abstract class BaseBuild extends TestBaseModule with ScalaJSDepsModule {
    override def scalaVersion = "2.13.16"
    override def scalaJSVersion = "1.19.0"

    abstract class BaseTestModule
        extends TestBaseModule
        with ScalaJSTests
        with TestModule.Munit {
      override def moduleKind = ModuleKind.CommonJSModule
      override def ivyDeps =
        super.ivyDeps() ++ Agg(
          ivy"org.scalameta::munit::0.7.29",
          ivy"io.github.nafg.scalajs-facades::react-phone-number-input_3::0.16.0"
        )
    }
  }
}
