package org.http4s
package server

import java.net.{InetSocketAddress, URL}
import fs2.Task
import org.http4s.dsl._
import org.specs2.specification.AfterAll
import scala.concurrent.ExecutionContext
import scala.io.Source

trait ServerContext extends AfterAll {
  def builder: ServerBuilder

  lazy val server = builder.bindAny()
    .withExecutionContext(ExecutionContext.global)
    .mountService(HttpService {
      case GET -> Root / "thread" / "routing" =>
        val thread = Thread.currentThread.getName
        Ok(thread)
      
      case GET -> Root / "thread" / "effect" =>
        Task.delay(Thread.currentThread.getName).flatMap(Ok(_))
    })
    .start
    .unsafeRun

  def afterAll = 
    server.shutdown.unsafeRun
}

trait ServerSpec extends Http4sSpec with ServerContext {
  def get(path: String): Task[String] = Task.delay {
    Source.fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path")).getLines.mkString
  }

  "A server" should {
    "route requests on the service executor" in {
      get("/thread/routing").unsafeRun must startWith("scala-execution-context-global-")
    }

    "execute the service task on the service executor" in {
      get("/thread/effect").unsafeRun must startWith("scala-execution-context-global-")
    }
  }
}
