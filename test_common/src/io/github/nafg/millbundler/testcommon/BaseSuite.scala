package io.github.nafg.millbundler.testcommon

import io.github.nafg.millbundler.jsdeps.ScalaJSDepsModule

import scala.concurrent.duration.DurationInt

import mill.*
import mill.javalib.JavaModule
import mill.javalib.testrunner.TestResult
import mill.scalajslib.api.ModuleKind
import mill.scalalib.DepSyntax
import mill.scalalib.TestModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester

class BaseSuite extends munit.FunSuite:
  override def munitTimeout = 4.minute

  protected val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  protected def multiEntryDir: os.Path = resourceFolder / "multi-entry"

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
          case Right(UnitTester.Result((_, results), _)) =>
            checkTestResults(results)
      }

  protected def evalTask[A](
      module: TestRootModule,
      resourceName: String,
      task: Task[A]
  ): A =
    UnitTester(module, resourceFolder / resourceName)
      .scoped { tester =>
        tester(task) match
          case Left(failing) => failing.throwException
          case Right(UnitTester.Result(value, _)) => value.asInstanceOf[A]
      }

  protected def assertExistingBundles(bundles: Seq[PathRef]): Unit =
    assert(bundles.nonEmpty, "expected bundle outputs")
    bundles.foreach { ref =>
      assert(os.exists(ref.path), s"missing bundle output ${ref.path}")
    }

  protected def evalTaskExpectFailure(
      module: TestRootModule,
      resourceName: String,
      task: Task[?]
  ): Throwable =
    try
      evalTask(module, resourceName, task)
      throw new AssertionError(s"Expected $task to fail")
    catch
      case e: AssertionError => throw e
      case e: Throwable      => e

  abstract class BaseBuild extends TestRootModule with ScalaJSDepsModule:
    override def scalaVersion = "3.8.2"
    override def scalaJSVersion = "1.22.0"

    abstract class BaseTestModule
        extends TestRootModule
        with ScalaJSTests
        with TestModule.Munit:
      /** Pin outer module — extra `TestScalaJSModule` mixins must not erase `JavaTests.moduleDeps`. */
      override def moduleDeps: Seq[JavaModule] = Seq(BaseBuild.this)

      override def moduleKind = ModuleKind.CommonJSModule

      override def mvnDeps =
        super.mvnDeps() ++ Seq(
          mvn"org.scalameta::munit::1.3.6",
          mvn"io.github.nafg.scalajs-facades::react-phone-number-input_3::0.19.0"
        )

    end BaseTestModule

  end BaseBuild

end BaseSuite
