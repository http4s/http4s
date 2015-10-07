package org.http4s.optics

import monocle.Lens
import monocle.macros.GenLens
import org.http4s._

object request {
  val method: Lens[Request, Method] = GenLens[Request](_.method)
  val uri: Lens[Request, Uri] = GenLens[Request](_.uri)
  val httpVersion: Lens[Request, HttpVersion] = GenLens[Request](_.httpVersion)
  val headers: Lens[Request, Headers] = GenLens[Request](_.headers)
  val body: Lens[Request, EntityBody] = GenLens[Request](_.body)
  val attributes: Lens[Request, AttributeMap] = GenLens[Request](_.attributes)
}
