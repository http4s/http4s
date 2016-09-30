package org.http4s
package dsl

import scalaz.ValidationNel
import scalaz.syntax.traverse._
import scalaz.std.list._
import scalaz.std.option._

object as {
  def unapply[A](ar: AuthedRequest[A]): Option[(Request, A)] = Some(ar.req -> ar.authInfo)
}
