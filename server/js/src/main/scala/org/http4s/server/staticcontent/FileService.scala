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
package staticcontent

import cats.data.OptionT
import cats.effect.kernel.Async
import fs2.io.file.Path

object FileService extends FileServiceShared {

  type PathRepr = Path

  protected object platformSpecific extends PlatformSpecific {
    def config[F[_]: Async](
        systemPath: String,
        pathPrefix: String,
        bufferSize: Int,
        cacheStrategy: CacheStrategy[F] = NoopCacheStrategy[F],
    ): Config[F] = {
      val pathCollector: PathCollector[F] = (f, c, r) => filesOnly(f, c, r)
      Config(systemPath, pathCollector, pathPrefix, bufferSize, cacheStrategy)
    }

    def invokePathCollector[F[_]](
        pathCollector: PathCollector[F]
    )(
        path: Path,
        config: Config[F],
        request: Request[F],
    ): OptionT[F, Response[F]] = pathCollector(path, config, request)

  }

}
