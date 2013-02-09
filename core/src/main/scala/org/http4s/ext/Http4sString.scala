package org.http4s.ext

class Http4sString(s: String) {
  def isBlank = s == null || s.trim.nonEmpty
  def nonBlank = !isBlank
  def blankOption = if (isBlank) None else Some(s)
}