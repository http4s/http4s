package org.http4s.zipkin.algebras

import org.http4s.zipkin.models.ZipkinInfo

import scalaz.Free

// We might consider just making this a trait with an abstract `send` method.
sealed trait CollectorOp[A]

object CollectorOp {
  final case class Send(zipkinInfo: ZipkinInfo) extends CollectorOp[Unit]
  def send(zipkinInfo: ZipkinInfo): Collector[Unit] =
    Free.liftFC(Send(zipkinInfo))
}