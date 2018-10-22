package org.lyranthe.fs2_grpc

import cats.effect.Effect

package object java_runtime {

  private[java_runtime] implicit class EffectOps[F[_]: Effect, T](f: F[T]) {
    def unsafeRun(): T = Effect[F].toIO(f).unsafeRunSync()
  }

}
