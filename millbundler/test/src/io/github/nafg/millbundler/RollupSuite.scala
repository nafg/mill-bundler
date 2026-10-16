package io.github.nafg.millbundler

import io.github.nafg.millbundler.testcommon.BaseSuite

import mill.*
import mill.api.Discover
import mill.scalajslib.api.{ModuleKind, ModuleSplitStyle}
import mill.testkit.TestRootModule
import mill.util.TokenReaders.*

class RollupSuite extends BaseSuite:

  test("Rollup") {
    object build extends BaseBuild:
      object test extends BaseTestModule with ScalaJSRollupModule.Test:
        override def rollupOutputName = Some("RollupTest")

        override def moduleKind = ModuleKind.ESModule

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[test.type]
    end build

    checkTestResults(build.test, "rollup")
  }

  test("Rollup - multi entry") {
    val inputs = Seq(multiEntryDir / "entry-a.js", multiEntryDir / "entry-b.js")

    object build extends BaseBuild:
      object bundleTest extends TestRootModule with ScalaJSRollupModule:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion

        def runMultiBundle = Task {
          bundle.apply()(BundleParams(inputs, opt = false))
        }

        def runRollupConfig = Task {
          rollupConfig()(BundleParams(inputs, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[bundleTest.type]
    end build

    val config =
      evalTask(build.bundleTest, "multi-entry", build.bundleTest.runRollupConfig)
    assert(config.contains("\"dir\":"))
    assert(config.contains("sourcemap: true"))
    assert(config.contains("entry-a.js"))
    assert(config.contains("entry-b.js"))
    assert(!config.contains("\"name\": \"RollupTest\""))

    val bundles =
      evalTask(build.bundleTest, "multi-entry", build.bundleTest.runMultiBundle)
    val outputs = bundles.map(_.path.last).toSet
    assert(outputs.contains("out-bundle-entry-a.js"))
    assert(outputs.contains("out-bundle-entry-b.js"))
    assert(outputs.contains("out-bundle-entry-a.js.map"))
    assert(outputs.contains("out-bundle-entry-b.js.map"))
    assertExistingBundles(bundles)
  }

  test("Rollup - empty input fails") {
    object build extends BaseBuild:
      object bundleTest extends TestRootModule with ScalaJSRollupModule:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion

        def runBundle = Task {
          bundle.apply()(BundleParams(Nil, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[bundleTest.type]
    end build

    val err =
      evalTaskExpectFailure(
        build.bundleTest,
        "multi-entry",
        build.bundleTest.runBundle
      )
    assert(
      err.getMessage.contains("No input files") ||
        Option(err.getCause).exists(_.getMessage.contains("No input files"))
    )
  }

  test("Rollup - split modules") {
    object build extends BaseBuild:
      object app extends TestRootModule with ScalaJSRollupModule:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion
        override def moduleKind = ModuleKind.CommonJSModule
        override def moduleSplitStyle = ModuleSplitStyle.FewestModules

        def runSplitBundle = Task {
          val report = fastLinkJS()
          val paths = getReportMainFilePath(report).toSeq
          assert(
            paths.size > 1,
            s"expected multiple public modules, got ${paths.size}: ${paths.mkString(", ")}"
          )
          bundle.apply()(BundleParams(paths, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[app.type]
    end build

    val bundles =
      evalTask(build.app, "rollup-split-app", build.app.runSplitBundle)
    assert(bundles.size >= 2)
    assertExistingBundles(bundles)
  }

  test("Rollup - output name in config") {
    val inputs = Seq(multiEntryDir / "entry-a.js")

    object build extends BaseBuild:
      object bundleTest extends TestRootModule with ScalaJSRollupModule:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion
        override def rollupOutputName = Some("RollupTest")

        def runRollupConfig = Task {
          rollupConfig()(BundleParams(inputs, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[bundleTest.type]
    end build

    val config =
      evalTask(build.bundleTest, "multi-entry", build.bundleTest.runRollupConfig)
    assert(config.contains("\"name\": \"RollupTest\""))
  }

end RollupSuite
