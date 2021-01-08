/*
 * Copyright 2016 http4s.org
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

package org.http4s.testing

import cats.effect.IO
import cats.effect.std.Dispatcher
import org.http4s.Http4sSuite

trait DispatcherIOFixture { this: Http4sSuite =>

  val dispatcher: Fixture[Dispatcher[IO]] = new Fixture[Dispatcher[IO]]("dispatcher") {
    var dispatcher: Dispatcher[IO] = _
    var shutdown: IO[Unit] = _
    override def apply(): Dispatcher[IO] = dispatcher

    override def beforeAll(): Unit = {
      val dispatcherAndShutdown = Dispatcher[IO].allocated.unsafeRunSync()
      dispatcher = dispatcherAndShutdown._1
      shutdown = dispatcherAndShutdown._2
    }

    override def afterAll(): Unit =
      shutdown.unsafeRunSync()
  }

}
