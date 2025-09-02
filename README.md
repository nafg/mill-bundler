# mill-bundler

A Mill plugin for managing NPM dependencies for Scala.js code, and bundling the output.

## Usage

### Managing NPM dependencies

To manage NPM dependencies, mix `ScalaJSNpmModule` into your Mill module.

If you have a dependency on a published library that defines `scalajs-bundler`'s `npmDependencies`, or otherwise
contains an `NPM_DEPENDENCIES` manifest file in its jar, those NPM dependencies will be automatically added. You can
also override the `jsDeps` target.

You can see the result by running `mill frontend.allJsDeps`.

```scala
import $ivy.`io.github.nafg.millbundler::jsdeps::0.1.0`, io.github.nafg.millbundler.jsdeps._


object frontend extends ScalaJSNpmModule {
  override def scalaVersion = "3.7.2"
  override def scalaJSVersion = "1.19.0"
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
import $ivy.`io.github.nafg.millbundler::millbundler::0.1.0`, io.github.nafg.millbundler._


object frontend extends ScalaJSRollupModule {
  // ...
}
```

You can then run `mill frontend.devBundle` or `mill frontend.prodBundle` to bundle your Scala.js code into a single
JavaScript file.

### Testing

`ScalaJSNpmModule`, `ScalaJSRollupModule`, `ScalaJSWebpackModule` provide a `Test` trait in their respective companion
objects. You can mix it into your test module to make sure your tests run after the NPM dependencies are installed, and
after the bundle is built.
