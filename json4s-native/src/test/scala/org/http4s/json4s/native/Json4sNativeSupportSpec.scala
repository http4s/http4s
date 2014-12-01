package org.http4s
package json4s
package native

import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JsonAST.JValue

class Json4sNativeSupportSpec extends JawnDecodeSupportSpec[JValue] with Json4sNativeSupport
