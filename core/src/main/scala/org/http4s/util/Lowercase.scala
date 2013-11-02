/**
 * hat tip to https://gist.github.com/jrudolph/7251633
 */
package org.http4s.util

import java.util.Locale
import scalaz.{Monad, @@, Tag}
import scalaz.syntax.{MonadOps, Ops}

/**
 * Represents a String has been converted to lowercase, and is suitable
 * for comparison in case-insensitive contexts.
 */
sealed trait Lowercase

/**
 * Represents a String has been converted to lowercase in the English
 * locale, and is suitable for comparison in case-insensitive contexts
 * regardless of default locale
 */
sealed trait LowercaseEn

trait LowercaseOps extends Ops[String] {
  def lowercase: String @@ Lowercase = lowercase(Locale.getDefault)
  def lowercase(locale: Locale): String @@ Lowercase = Tag[String, Lowercase](self.toLowerCase)
  def lowercaseEn: String @@ LowercaseEn = Tag[String, LowercaseEn](self.toLowerCase(Locale.ENGLISH))
}

trait LowercaseSyntax {
  type CiString = String @@ LowercaseEn

  implicit def ToLowercaseOps(s: String): LowercaseOps = new LowercaseOps {
    def self = s
  }
}