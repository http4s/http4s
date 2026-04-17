package org.http4s.multipart

import cats.effect.kernel.Async
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.Entity

import java.nio.file.{Files => NioFiles}
import scala.util.Using

private[multipart] trait PartReceiverPlatform {
  def writeToFile[F[_]](path: Path, entity: Entity[F])(implicit F: Files[F], A: Async[F]): F[Unit] =
    entity match {
      case Entity.Empty =>
        A.unit
      case Entity.Strict(bv) =>
        A.blocking {
          Using.resource(NioFiles.newOutputStream(path.toNioPath)) { out =>
            bv.copyToStream(out)
          }
        }
      case Entity.Streamed(body, _) =>
        body.through(F.writeAll(path)).compile.drain
    }
}

private[multipart] object PartReceiverPlatform extends PartReceiverPlatform
