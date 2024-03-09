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

package org.http4s.h2

import cats.effect._
import org.typelevel.vault._

object H2Keys {
  // Client Side Key To Try Http2-Prior-Knowledge
  // which means immediately using http2 without any upgrade mechanism
  // but is invalid if the receiving server does not support the
  // mechanism.
  val Http2PriorKnowledge: Key[Unit] = Key.newKey[SyncIO, Unit].unsafeRunSync()

}
