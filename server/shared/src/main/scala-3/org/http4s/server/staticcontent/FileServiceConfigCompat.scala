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

import scala.annotation.nowarn
import scala.deriving.Mirror

private[staticcontent] trait FileServiceConfigCompanionCompat extends Mirror.Product {
  type MirroredMonoType = FileService.Config[_]

  @deprecated(
    "Config is no longer a case class. The Config.fromProduct method is provided for binary compatibility.",
    "0.23.8",
  )
  def fromProduct(p: Product): MirroredMonoType = {
    type F[_] = Any
    FileService.Config.apply[F](
      p.productElement(0).asInstanceOf[String],
      p.productElement(1).asInstanceOf[FileService.PathCollector[F]],
      p.productElement(2).asInstanceOf[String],
      p.productElement(3).asInstanceOf[Int],
      p.productElement(4).asInstanceOf[CacheStrategy[F]],
    ): @nowarn("msg=deprecated")
  }

  @deprecated(
    "Config is no longer a case class. The Config.unapply method is provided for binary compatibility.",
    "0.23.8",
  )
  def unapply[F[_]](config: FileService.Config[F]): FileService.Config[F] = config
}
