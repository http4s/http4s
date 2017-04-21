package org.http4s
package dsl

object as {
  def unapply[F[_], A](ar: AuthedRequest[F, A]): Option[(Request[F], A)] =
    Some(ar.req -> ar.authInfo)
}
