package org.http4s
package dsl

object as {
  def unapply[A](ar: AuthedRequest[A]): Option[(Request, A)] = Some(ar.req -> ar.authInfo)
}
