/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.implicits._
import cats._
import cats.data.Kleisli

object Router {

  /**
    * Defines an [[HttpRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Monad](mappings: (String, HttpRoutes[F])*): HttpRoutes[F] =
    define(mappings: _*)(HttpRoutes.empty[F])

  /**
    * Defines an [[HttpRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incomming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Monad](mappings: (String, HttpRoutes[F])*)(
      default: HttpRoutes[F]): HttpRoutes[F] =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, routes)) =>
        val prefixPath = Uri.Path.fromString(prefix)
        if (prefixPath.isEmpty) routes <+> acc
        else
          Kleisli { req =>
            (
              if (req.pathInfo.startsWith(prefixPath))
                routes.local(translate(prefixPath)) <+> acc
              else
                acc
            )(req)
          }
    }

  private[server] def translate[F[_]: Functor](prefix: Uri.Path)(req: Request[F]): Request[F] = {
    val newCaret = req.pathInfo.findSplit(prefix)
    val oldCaret = req.attributes.lookup(Request.Keys.PathInfoCaret)
    val resultCaret = oldCaret |+| newCaret
    resultCaret match {
      case Some(index) => req.withAttribute(Request.Keys.PathInfoCaret, index)
      case None => req
    }
  }
}
