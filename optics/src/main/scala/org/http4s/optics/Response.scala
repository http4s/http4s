package org.http4s.optics

import monocle.Lens
import monocle.macros.GenLens
import org.http4s._

object response {
  val method: Lens[Response, Status] = GenLens[Response](_.status)
  val httpVersion: Lens[Response, HttpVersion] = GenLens[Response](_.httpVersion)
  val headers: Lens[Response, Headers] = GenLens[Response](_.headers)
  val body: Lens[Response, EntityBody] = GenLens[Response](_.body)
  val attributes: Lens[Response, AttributeMap] = GenLens[Response](_.attributes)
}
