import io.github.nafg.scalajs.facades.reactphonenumberinput.*
import munit.Assertions.*

import scala.scalajs.js
import scala.scalajs.js.annotation.*

class Tests extends munit.FunSuite:

  test("ReactPhoneNumberInput is defined (webpack-simple)") {
    assert(!js.isUndefined(ReactPhoneNumberInput.raw))
  }
