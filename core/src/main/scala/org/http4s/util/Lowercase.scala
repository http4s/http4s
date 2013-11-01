/**
 * hat tip to https://gist.github.com/jrudolph/7251633
 */
package org.http4s.util

import java.util.Locale
import scalaz.{Monad, @@, Tag}
import scalaz.syntax.{MonadOps, Ops}

sealed trait Lowercase

trait LowercaseOps extends Ops[String] {
  def lowercase: String @@ Lowercase =
    Tag[String, Lowercase](self.toLowerCase)
  def lowercase(locale: Locale): String @@ Lowercase =
    Tag[String, Lowercase](self.toLowerCase(locale))
}

trait LowercaseSyntax {
  implicit def ToLowercaseOps(s: String): LowercaseOps = new LowercaseOps {
    def self = s
  }
}