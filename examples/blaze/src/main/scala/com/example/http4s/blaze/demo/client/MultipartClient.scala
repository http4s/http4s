package com.example.http4s.blaze.demo.client

import java.net.URL

import cats.effect.{Effect, IO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.example.http4s.blaze.demo.StreamUtils
import fs2.StreamApp.ExitCode
import fs2.{Scheduler, Stream, StreamApp}
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`
import org.http4s.Method._
import org.http4s.multipart.{Multipart, Part}
import org.http4s.{MediaType, Uri}

object MultipartClient extends MultipartHttpClient[IO]

class MultipartHttpClient[F[_]](implicit F: Effect[F], S: StreamUtils[F])
    extends StreamApp
    with Http4sClientDsl[F] {

  private val image: F[URL] = F.delay(getClass.getResource("/beerbottle.png"))

  private def multipart(url: URL) = Multipart[F](
    Vector(
      Part.formData("name", "gvolpe"),
      Part.fileData("rick", url, `Content-Type`(MediaType.image.png))
    )
  )

  private val request =
    for {
      body <- image.map(multipart)
      req <- POST(Uri.uri("http://localhost:8080/v1/multipart"), body)
    } yield req.replaceAllHeaders(body.headers)

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    Scheduler(corePoolSize = 2).flatMap { implicit scheduler =>
      for {
        client <- Http1Client.stream[F]()
        req <- Stream.eval(request)
        value <- Stream.eval(client.expect[String](req))
        _ <- S.evalF(println(value))
      } yield ()
    }.drain

}
