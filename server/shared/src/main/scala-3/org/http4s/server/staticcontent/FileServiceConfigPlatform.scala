package org.http4s
package server
package staticcontent

import scala.annotation.nowarn
import scala.deriving.Mirror

private[staticcontent] trait FileServiceConfigCompanionPlatform extends Mirror.Product {
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
