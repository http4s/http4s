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
package server.middleware.authentication

import cats.effect.IO
import cats.effect.testkit.TestControl

import scala.concurrent.duration._

class NonceKeeperFSuite extends Http4sSuite {
  test("stale nonces are evicted once they age past staleTimeout") {
    TestControl.executeEmbed {
      val staleTimeout = 1.hour
      val nonceCleanupInterval = 1.hour
      NonceKeeperF[IO](
        staleTimeout = staleTimeout,
        nonceCleanupInterval = nonceCleanupInterval,
        bits = 16,
        maxNonces = 1000000,
      ).flatMap { keeper =>
        for {
          data <- keeper.newNonce()
          _ <- IO.sleep(staleTimeout + nonceCleanupInterval + 1.hour)
          _ <- keeper.newNonce()
          reply <- keeper.receiveNonce(data, 1)
        } yield assertEquals(reply, NonceKeeper.StaleReply)
      }
    }
  }

  test("the nonce map is bounded by maxNonces, evicting the oldest first") {
    NonceKeeperF[IO](
      staleTimeout = 1.hour,
      nonceCleanupInterval = 1.hour,
      bits = 32,
      maxNonces = 3,
    ).flatMap { keeper =>
      for {
        first <- keeper.newNonce()
        _ <- keeper.newNonce()
        atCap <- keeper.receiveNonce(first, 1)
        _ <- keeper.newNonce()
        pastCap <- keeper.receiveNonce(first, 2)
      } yield {
        assertEquals(atCap, NonceKeeper.OKReply)
        assertEquals(pastCap, NonceKeeper.StaleReply)
      }
    }
  }
}
