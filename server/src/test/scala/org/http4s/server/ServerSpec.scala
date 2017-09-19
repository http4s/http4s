package org.http4s
package server

import java.net.{InetSocketAddress, URL}
import org.http4s.internal.compatibility._
import org.http4s.dsl._
import org.specs2.specification.AfterAll
import scala.io.Source
import scalaz.{\/-, -\/}
import scalaz.concurrent.Task

trait ServerContext extends AfterAll {
  def builder: ServerBuilder

  lazy val server = builder.bindAny()
    .withServiceExecutor(Http4sSpec.TestPool)
    .mountService(HttpService {
      case GET -> Root / "thread" / "routing" =>
        val thread = Thread.currentThread.getName
        Ok(thread)
      
      case GET -> Root / "thread" / "effect" =>
        Task.delay(Thread.currentThread.getName).flatMap(Ok(_))
    })
    .start
    .unsafePerformSync

  def afterAll = 
    server.shutdown.unsafePerformSync
}

trait ServerSpec extends Http4sSpec with ServerContext {
  def get(path: String): Task[String] = Task.delay {
    Source.fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path")).getLines.mkString
  }

  "A server" should {
    "route requests on the service executor" in {
      get("/thread/routing").unsafePerformSync must startWith("http4s-spec-")
    }

    "execute the service task on the service executor" in {
      get("/thread/effect").unsafePerformSync must startWith("http4s-spec-")
    }
  }
}
