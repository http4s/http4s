package org.http4s
package json4s
package native

import org.json4s.JsonMethods
import scala.text.Document

trait Json4sNativeSupport extends Json4sSupport[Document] {
  override protected def jsonMethods = org.json4s.native.JsonMethods
}

object Json4sNativeSupport extends Json4sNativeSupport
