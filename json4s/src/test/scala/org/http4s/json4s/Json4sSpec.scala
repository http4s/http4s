package org.http4s.json4s

import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JValue

trait Json4sSpec[J] extends JawnDecodeSupportSpec[JValue] { self: Json4sInstances[J] =>
  testJsonDecoder(json)
}
