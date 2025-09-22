/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server

import cats.Functor
import cats.syntax.functor._
import cats.~>
import org.typelevel.log4cats
import org.typelevel.log4cats.LoggerFactoryGen

package object middleware {

  implicit private[middleware] class LoggerFactoryGenOps[F[_]](private val lf: LoggerFactoryGen[F])
      extends AnyVal {

    def mapK[G[_]](fk: F ~> G)(implicit F: Functor[F]): LoggerFactoryGen[G] =
      new LoggerFactoryGen[G] {
        type LoggerType = log4cats.Logger[G]

        def getLoggerFromName(name: String): LoggerType =
          lf.getLoggerFromName(name).mapK(fk)

        def fromName(name: String): G[LoggerType] = {
          val logger = lf.fromName(name).map(_.mapK(fk))
          fk(logger)
        }
      }

  }
}
