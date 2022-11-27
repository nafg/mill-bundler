import io.github.nafg.scalajs.facades.reactphonenumberinput._
import munit.Assertions._
import scala.scalajs.js
import scala.scalajs.js.annotation._


class Tests extends munit.FunSuite {
  test("ReactPhoneNumberInput") {
    assert(!js.isUndefined(ReactPhoneNumberInput.raw))
  }
}
