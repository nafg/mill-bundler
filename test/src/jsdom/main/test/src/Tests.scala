import munit.Assertions._
import scala.scalajs.js
import scala.scalajs.js.annotation._


@js.native
@JSImport("jsdom", "JSDOM")
class JSDOM extends js.Object {
  def window: js.Dynamic = js.native
}

class Tests extends munit.FunSuite {
  test("JsDom") {
    val dom = new JSDOM
    dom.window.localStorage.setItem("key", "value")
    assert(dom.window.localStorage.getItem("key") == "value")
  }
}
