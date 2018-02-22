package com.example.http4s.blaze.demo.client

import cats.effect.{Effect, IO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.StreamApp.ExitCode
import fs2.{Scheduler, Stream, StreamApp}
import org.http4s.client.blaze.Http1Client
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart, Part}
import org.http4s.{MediaType, Method, Request, Uri}

object MultipartClient extends MultipartHttpClient[IO]

class MultipartHttpClient[F[_]](implicit F: Effect[F]) extends StreamApp {

  private val rick = getClass.getResource("/beerbottle.png")

  private val multipart = Multipart[F](
    Vector(
      Part.formData("name", "gvolpe"),
      Part.fileData("rick", rick, `Content-Type`(MediaType.`image/png`))
    )
  )

  private val request =
    Request[F](method = Method.POST, uri = Uri.uri("http://localhost:8080/v1/multipart"))
      .withBody(multipart)
      .map(_.replaceAllHeaders(multipart.headers))

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] = {
    Scheduler(corePoolSize = 2).flatMap { implicit scheduler =>
      Stream.eval(
        for {
          client  <- Http1Client[F]()
          req     <- request
          _       <- client.expect[String](req).map(println)
        } yield ()
      )
    }.drain
  }

}
