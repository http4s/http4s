package org.http4s

trait PlatformCasing {
  def toUpperCase(s: String): String =
    s.toUpperCase()
  def toLowerCase(s: String): String =
    s.toLowerCase()
}
