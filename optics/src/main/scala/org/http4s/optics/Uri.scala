package org.http4s.optics

import monocle.Lens
import monocle.macros.GenLens
import org.http4s.{Query, Uri}
import org.http4s.Uri._
import org.http4s.util.CaseInsensitiveString

object uri {
  val scheme: Lens[Uri, Option[CaseInsensitiveString]] = GenLens[Uri](_.scheme)
  val authority: Lens[Uri, Option[Authority]] = GenLens[Uri](_.authority)
  val path: Lens[Uri, Path] = GenLens[Uri](_.path)
  val query: Lens[Uri, Query] = GenLens[Uri](_.query)
  val fragment: Lens[Uri, Option[Fragment]] = GenLens[Uri](_.fragment)
}