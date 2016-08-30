package org.http4s.zipkin.algebras

import org.http4s.zipkin.models.ZipkinInfo

import scalaz.concurrent.Task

trait Collector {
  def send(zipkinInfo: ZipkinInfo): Unit
}

object Collector {
  def send(zipkinInfo: ZipkinInfo)(collector: Collector): Task[Unit] = Task.delay {
    collector.send(zipkinInfo)
  }
}
