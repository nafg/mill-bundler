# mill-bundler

A Mill plugin for managing NPM dependencies for Scala.js code, and bundling the output.

This repository is the maintained fork of [nafg/mill-bundler](https://github.com/nafg/mill-bundler). Published artifacts use the `io.github.thijsbroersen.millbundler` group id. Scala packages stay `io.github.nafg.millbundler`.

## Usage

### Managing NPM dependencies

To manage NPM dependencies, mix `ScalaJSNpmModule` into your Mill module.

If you have a dependency on a published library that defines `scalajs-bundler`'s `npmDependencies`, or otherwise
contains an `NPM_DEPENDENCIES` manifest file in its jar, those NPM dependencies will be automatically added. You can
also override the `jsDeps` target.

You can see the result by running `mill frontend.allJsDeps`.

```scala
import $ivy.`io.github.thijsbroersen.millbundler::jsdeps_mill1::0.1.0`, io.github.nafg.millbundler.jsdeps._


object frontend extends ScalaJSNpmModule {
  override def scalaVersion = "3.8.2"
  override def scalaJSVersion = "1.22.0"
  override def jsDeps =
    super.jsDeps() ++
      JsDeps(
        dependencies = Map(
          "react" -> "17.0.2",
          "react-dom" -> "^17"
        ),
        devDependencies = Map(
          "typescript" -> "*"
        ),
        jsSources = Map(
          "demo.js" -> "console.log('hello world')"
        )
      )
}
```

The trait provides an `npmInstall` target, which will run `npm install` in the directory specified by the `jsDepsDir`
target, which defaults to the one allocated for it by Mill.

### Bundling

To bundle your Scala.js code, mix in `ScalaJSRollupModule` or `ScalaJSWebpackModule` into your module. They extend
ScalaJSNpmModule, so everything from the previous section applies.

```scala
import $ivy.`io.github.thijsbroersen.millbundler::millbundler_mill1::0.1.0`, io.github.nafg.millbundler._


object frontend extends ScalaJSRollupModule {
  // ...
}
```

You can then run `mill frontend.devBundle` or `mill frontend.prodBundle` to bundle your Scala.js code.

When the Scala.js linker produces a single public module, bundling writes one output file (`bundleFilename`, default
`out-bundle.js`). When the linker produces multiple public modules, pass all entry files to the bundler automatically
via `getReportMainFilePath`; outputs are written using `outputEntryFileNames` (default `out-bundle-[name].js`), where
`[name]` is derived from each input file name.

Override `bundleFilename` / `outputEntryFileNames` to customize output naming. For Rollup, `rollupOutputName` sets the
IIFE global name when bundling a single entry.

### Testing

`ScalaJSNpmModule`, `ScalaJSRollupModule`, `ScalaJSWebpackModule` provide a `Test` trait in their respective companion
objects. You can mix it into your test module to make sure your tests run after the NPM dependencies are installed, and
after the bundle is built.

## Publishing

Requires Mill 1.1.10 or newer. The plugin is compiled for Mill binary platform `1`, so the artifact ids end in `_mill1`.

| Module | Ivy coordinate |
| --- | --- |
| jsdeps | `io.github.thijsbroersen.millbundler::jsdeps_mill1::<version>` |
| millbundler | `io.github.thijsbroersen.millbundler::millbundler_mill1::<version>` |

The version comes from the nearest `v*` git tag (`untaggedSuffix = -SNAPSHOT`). Pushing tag `v0.1.0` to this repository publishes `0.1.0` to Maven Central.

Before the first release:

1. Register the `io.github.thijsbroersen` namespace on the [Central Portal](https://central.sonatype.com/). The group `io.github.thijsbroersen.millbundler` is covered by that namespace.
2. Add these GitHub Actions secrets: `MILL_SONATYPE_USERNAME`, `MILL_SONATYPE_PASSWORD`, `MILL_PGP_SECRET_BASE64`, `MILL_PGP_PASSPHRASE`.

The publish job runs `./mill mill.scalalib.SonatypeCentralPublishModule/` on `v*` tags. It checks out full git history so the tag version resolves.
