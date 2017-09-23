package org.http4s
package server

import java.net.URL
import fs2.Task
import org.http4s.dsl._
import org.specs2.specification.AfterAll
import scala.io.Source

trait ServerSpec extends Http4sSpec with AfterAll {
  def builder: ServerBuilder

  val server =
    builder
      .bindAny()
      .withExecutionContext(Http4sSpec.TestExecutionContext)
      .mountService(HttpService {
        case GET -> Root / "thread" / "routing" =>
          val thread = Thread.currentThread.getName
          Ok(thread)

        case GET -> Root / "thread" / "effect" =>
          Task.delay(Thread.currentThread.getName).flatMap(Ok(_))
      })
      .start
      .unsafeRun()

  def afterAll = server.shutdownNow()

  // This should be in IO and shifted but I'm tired of fighting this.
  private def get(path: String): String =
    Source
      .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
      .getLines
      .mkString

  "A server" should {
    val globalExecutorThreadPrefix = BuildInfo.scalaVersion match {
      case v if v.startsWith("2.11.") => "ForkJoinPool-"
      case _ => "scala-execution-context-global-"
    }

    "route requests on the service executor" in {
      println("foo")
      get("/thread/routing") must startWith("http4s-spec-")
    }

    "execute the service task on the service executor" in {
      println("foo")
      get("/thread/effect") must startWith("http4s-spec-")
    }
  }
}
