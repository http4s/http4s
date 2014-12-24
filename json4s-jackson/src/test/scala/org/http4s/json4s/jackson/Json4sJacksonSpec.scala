package org.http4s.json4s.jackson

import _root_.jawn.support.json4s.Parser.facade
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JsonAST.JValue

class Json4sJacksonSpec extends JawnDecodeSupportSpec[JValue] {
  testJawnDecoder()
}
