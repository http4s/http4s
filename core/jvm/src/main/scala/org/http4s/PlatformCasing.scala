package org.http4s

import java.util.Locale

trait PlatformCasing {
  def toUpperCase(s: String): String =
    s.toUpperCase(Locale.ROOT)
  def toLowerCase(s: String): String =
    s.toLowerCase(Locale.ROOT)
}
