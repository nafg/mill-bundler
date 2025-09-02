package io.github.nafg.millbundler

case class BundleParams(inputFiles: Seq[os.Path], opt: Boolean)

object BundleParams {
  def apply(inputFile: os.Path, opt: Boolean): BundleParams =
    BundleParams(Seq(inputFile), opt)
}
