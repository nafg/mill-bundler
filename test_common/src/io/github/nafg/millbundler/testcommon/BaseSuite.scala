package io.github.nafg.millbundler.testcommon

import io.github.nafg.millbundler.jsdeps.ScalaJSDepsModule

import scala.concurrent.duration.DurationInt

import mill.*
import mill.javalib.testrunner.TestResult
import mill.scalajslib.api.ModuleKind
import mill.scalalib.DepSyntax
import mill.scalalib.TestModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester

class BaseSuite extends munit.FunSuite:
  override def munitTimeout = 4.minute

  private val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  protected def checkTestResults(testResults: Seq[TestResult]): Unit =
    assert(testResults.nonEmpty, "No tests found")
    assert(
      testResults.forall(_.status == "Success"),
      "Some tests failed"
    )

  protected def checkTestResults(
      test: BaseBuild#BaseTestModule,
      name: String
  ): Unit =
    UnitTester(test, resourceFolder / name)
      .scoped { tester =>
        tester(test.testCached) match
          case Left(failing) => failing.throwException
          case Right(UnitTester.Result((_, result), _)) =>
            checkTestResults(result)
      }

  abstract class BaseBuild extends TestRootModule with ScalaJSDepsModule:
    override def scalaVersion = "3.7.2"
    override def scalaJSVersion = "1.19.0"

    abstract class BaseTestModule
        extends TestRootModule
        with ScalaJSTests
        with TestModule.Munit:
      override def moduleKind = ModuleKind.CommonJSModule

      override def mvnDeps =
        super.mvnDeps() ++ Seq(
          mvn"org.scalameta::munit::1.1.1",
          mvn"io.github.nafg.scalajs-facades::react-phone-number-input_3::0.16.0"
        )

    end BaseTestModule

  end BaseBuild

end BaseSuite
