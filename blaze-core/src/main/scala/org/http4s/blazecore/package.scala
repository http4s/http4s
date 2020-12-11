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

import cats.effect.{Resource, Sync}
import org.http4s.blaze.util.{Cancelable, TickWheelExecutor}

package object blazecore {

  private[http4s] def tickWheelResource[F[_]](implicit F: Sync[F]): Resource[F, TickWheelExecutor] =
    Resource(F.delay {
      val s = new TickWheelExecutor()
      (s, F.delay(s.shutdown()))
    })

  private[blazecore] val NoOpCancelable = new Cancelable {
    def cancel() = ()
    override def toString = "no op cancelable"
  }
}
