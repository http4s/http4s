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

package org.http4s.blazecore

import cats.effect.Async
import cats.syntax.all._

import scala.concurrent.ExecutionContext

private[http4s] sealed trait ExecutionContextConfig extends Product with Serializable {
  def getExecutionContext[F[_]: Async]: F[ExecutionContext] = this match {
    case ExecutionContextConfig.DefaultContext => Async[F].executionContext
    case ExecutionContextConfig.ExplicitContext(ec) => ec.pure[F]
  }
}

private[http4s] object ExecutionContextConfig {
  case object DefaultContext extends ExecutionContextConfig
  final case class ExplicitContext(executionContext: ExecutionContext)
      extends ExecutionContextConfig
}
