package org.http4s.json4s.jackson

import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JsonAST.JValue

class Json4sJacksonSupportSpec extends JawnDecodeSupportSpec[JValue] with Json4sJacksonSupport
