package org.http4s.optics

import monocle.Lens
import org.http4s.Header
import org.http4s.Header.Raw
import org.http4s.util.CaseInsensitiveString

object header {
  val name: Lens[Header, CaseInsensitiveString] = Lens[Header, CaseInsensitiveString](_.name)(n => h => Raw(n, h.value))
  val value: Lens[Header, String] = Lens[Header, String](_.value)(v => h => Header(h.name.value, v))
}