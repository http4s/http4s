/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.effect.Async

package object servlet {
  protected[servlet] type BodyWriter[F[_]] = Response[F] => F[Unit]

  protected[servlet] def NullBodyWriter[F[_]](implicit F: Async[F]): BodyWriter[F] =
    _ => F.unit

  protected[servlet] val DefaultChunkSize = 4096
}
