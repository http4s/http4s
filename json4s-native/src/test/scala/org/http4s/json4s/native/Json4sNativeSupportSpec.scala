package org.http4s
package json4s
package native

import _root_.jawn.support.json4s.Parser.facade
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JsonAST.JValue

class Json4sNativeSupportSpec extends JawnDecodeSupportSpec[JValue] {
  testJsonDecoder(json)
}
