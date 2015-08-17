package org.http4s
package docs

import org.specs2.mutable.Specification
import org.http4s.server._
import org.http4s.dsl._

class CompositionExample extends Specification {

  "Composition should be easy" in {
    /// code_ref: composition_example
    val service = HttpService { case req => Ok("Foo") }
    val wcompression = middleware.GZip(service)
    val translated   = middleware.URITranslation.translateRoot("/http4s")(service)
    /// end_code_ref
    ok
  }
}

