package io.github.nafg.millbundler

case class BundleParams(inputFiles: Iterable[os.Path], opt: Boolean)

object BundleParams:

  def apply(inputFile: os.Path, opt: Boolean): BundleParams =
    BundleParams(Seq(inputFile), opt)
