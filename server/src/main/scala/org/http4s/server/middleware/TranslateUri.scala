/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server.middleware

import cats.data.Kleisli
import cats.{Functor, MonoidK}
import cats.implicits._

/** Removes the given prefix from the beginning of the path of the [[Request]].
  */
object TranslateUri {
  def apply[F[_], G[_], B](prefix: String)(http: Kleisli[F, Request[G], B])(implicit
      F: MonoidK[F],
      G: Functor[G]): Kleisli[F, Request[G], B] =
    if (prefix.isEmpty || prefix == "/") http
    else {
      val prefixAsPath = Uri.Path.fromString(prefix)

      Kleisli { (req: Request[G]) =>
        val newCaret = req.pathInfo.indexOf(prefixAsPath)
        val shouldTranslate = req.pathInfo.startsWith(prefixAsPath)
        if (shouldTranslate) http(setCaret(req, newCaret))
        else F.empty
      }
    }

  private def setCaret[F[_]: Functor](req: Request[F], newCaret: Option[Int]): Request[F] = {
    val oldCaret = req.attributes.lookup(Request.Keys.PathInfoCaret)
    val combined = oldCaret |+| newCaret
    combined match {
      case Some(value) => req.withAttribute(Request.Keys.PathInfoCaret, value)
      case None => req
    }
  }
}
