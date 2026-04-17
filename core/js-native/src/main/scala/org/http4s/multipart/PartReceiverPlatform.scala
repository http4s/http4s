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

package org.http4s.multipart

import cats.effect.kernel.Async
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.Entity

private[multipart] trait PartReceiverPlatform {
  def writeToFile[F[_]](path: Path, entity: Entity[F])(implicit F: Files[F], A: Async[F]): F[Unit] =
    entity match {
      case Entity.Empty => A.unit
      case Entity.Strict(_) | Entity.Streamed(_, _) =>
        entity.body.through(F.writeAll(path)).compile.drain
    }
}
