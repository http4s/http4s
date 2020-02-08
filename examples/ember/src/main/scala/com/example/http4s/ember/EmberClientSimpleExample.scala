package com.example.http4s.ember

import cats._
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.client._
import org.http4s.circe._
import org.http4s.implicits._

import _root_.io.circe.Json
import _root_.org.http4s.ember.client.EmberClientBuilder
import fs2._
import _root_.io.chrisdavenport.log4cats.Logger
import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scodec.bits.ByteVector

object EmberClientSimpleExample extends IOApp {

  val githubReq = Request[IO](Method.GET, uri"http://christopherdavenport.github.io/")
  val dadJokeReq = Request[IO](Method.GET, uri"https://icanhazdadjoke.com/")
  val googleReq = Request[IO](Method.GET, uri"https://www.google.com/")
  val httpBinGet = Request[IO](Method.GET, uri"https://httpbin.org/get")
  val httpBinPng = Request[IO](Method.GET, uri"https://httpbin.org/image/png")

  val logger = Slf4jLogger.getLogger[IO]

  def run(args: List[String]): IO[ExitCode] = {
    EmberClientBuilder
      .default[IO]
      .build
      .use(client =>
        logTimed(
          logger,
          "Http Only - ChristopherDavenport Site",
          getRequestBufferedBody(client, githubReq)) >>
          logTimed(logger, "Json - DadJoke", client.expect[Json](dadJokeReq)) >>
          logTimed(logger, "Https - Google", getRequestBufferedBody(client, googleReq)) >>
          logTimed(
            logger,
            "Small Response - HttpBin Get",
            getRequestBufferedBody(client, httpBinGet)) >>
          logTimed(
            logger,
            "Large Response - HttpBin PNG",
            getRequestBufferedBody(client, httpBinPng)) >>
          IO(println("Done")))

  }.as(ExitCode.Success)

  def getRequestBufferedBody[F[_]: Sync](client: Client[F], req: Request[F]): F[Response[F]] =
    client
      .run(req)
      .use(resp =>
        resp.body.compile
          .to(ByteVector)
          .map(bv => resp.copy(body = Stream.chunk(Chunk.ByteVectorChunk(bv)))))

  def logTimed[F[_]: Clock: Monad, A](logger: Logger[F], name: String, fa: F[A]): F[A] =
    timedMS(fa).flatMap {
      case (time, action) =>
        logger.info(s"Action $name took $time").as(action)
    }

  def timedMS[F[_]: Clock: Applicative, A](fa: F[A]): F[(FiniteDuration, A)] = {
    val nowMS = Clock[F].monotonic(TimeUnit.MILLISECONDS)
    (nowMS, fa, nowMS).mapN {
      case (before, result, after) =>
        val time = (after - before).millis
        (time, result)
    }
  }

}
