/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import cats.effect.IO
import munit._

import scala.concurrent.duration._

class ShutdownSuite extends CatsEffectSuite {

  test("timed shutdown is shutting down while connections drain") {
    for {
      shutdown <- Shutdown[IO](10.seconds)
      _ <- shutdown.isShuttingDown.assertEquals(false)
      _ <- shutdown.newConnection
      awaiting <- shutdown.await.start
      _ <- shutdown.signal
      _ <- shutdown.isShuttingDown.assertEquals(true)
      _ <- shutdown.removeConnection
      _ <- awaiting.joinWithNever
    } yield ()
  }

  test("immediate shutdown is shutting down after await") {
    for {
      shutdown <- Shutdown[IO](0.nanos)
      _ <- shutdown.isShuttingDown.assertEquals(false)
      _ <- shutdown.await
      _ <- shutdown.isShuttingDown.assertEquals(true)
    } yield ()
  }
}
