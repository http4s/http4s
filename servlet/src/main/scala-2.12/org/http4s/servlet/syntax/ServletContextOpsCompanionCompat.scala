/*
 * Copyright 2013 http4s.org
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
package servlet
package syntax

import cats.effect._
import cats.effect.std.Dispatcher
import org.http4s.server.defaults

import javax.servlet.ServletContext
import javax.servlet.ServletRegistration

private[syntax] trait ServletContextOpsCompanionCompat {

  @deprecated("Preserved for binary compatibility", "0.23.11")
  def `mountHttpApp$extension`[F[_]: Async](
      servlet: ServletContext,
      name: String,
      service: HttpApp[F],
      mapping: String,
      dispatcher: Dispatcher[F],
  ): ServletRegistration.Dynamic =
    (new ServletContextOps(servlet)).mountHttpApp(
      name,
      service,
      mapping,
      dispatcher,
      defaults.ResponseTimeout,
    )
}
