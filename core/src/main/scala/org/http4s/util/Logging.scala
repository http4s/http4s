package org.http4s.util

import cats._
import cats.implicits._
import cats.data.Kleisli
import cats.effect.{Effect, Sync}
import org.http4s.Method.NoBody
import org.http4s._
//import org.http4s.Method._
import org.log4s.Logger
import org.log4s.LogLevel
import fs2._

import scala.concurrent.ExecutionContext

object Logging {


  def logMessage[F[_], A <: Message[F]](
                                        f: Kleisli[F, A, String],
                                        cacheBody: A => Boolean = {_: A => true},
                                        forceBody: A => Boolean = {_: A => false},
                                        logLevel: A => LogLevel = {_: A => org.log4s.Trace},
                                        a: A,
                                        logger: Logger)
                                      (implicit F: Effect[F], ec: ExecutionContext): F[a.Self] = {

    if (!cacheBody(a)){
      f(a).flatMap(s => F.delay(logger(logLevel(a))(s))).as(a.withBodyStream(a.body)) // Useless conversion for a.Self
    } else if (forceBody(a)) {
      for {
        immutableBody <- a.body.runLog
        newMessage = a.withBodyStream(Stream.emits(immutableBody))
        messageString <- f(newMessage)
        _ <- F.delay(logger(logLevel(a))(messageString))
      } yield newMessage
    } else {
        for {
          bodyVec <- fs2.async.refOf(Vector.empty[Byte])
          concealedRequest <- F.delay{
            val newBody = Stream.eval(bodyVec.get).flatMap(v => Stream.emits(v).covary[F])
            val loggedMessage = a.withBodyStream(newBody)
            a.withBodyStream(
              a.body
                .observe(_.chunks.flatMap(c => Stream.eval_(bodyVec.modify(_ ++ c.toVector))))
                .onFinalize(f(loggedMessage).flatMap(s => F.delay(logger(logLevel(a))(s)))) // Does Not Log On Empty EntityBody
            )
          }
        } yield concealedRequest
      }
  }

  def requestLogger[F[_]: Effect](r: Request[F], l: Logger)(implicit ec: ExecutionContext): F[Request[F]] = {
    def forceBody(r: Request[F]): Boolean = r.method match {
      case _: NoBody => true
      case _ => false
    }
    def cacheBody(r: Request[F]): Boolean = true

    logMessage(
      Kleisli(logRequest[F]),
      cacheBody,
      forceBody,
      _ => org.log4s.Trace,
      r,
      l
    )
  }

  def logRequest[F[_]: Sync](r: Request[F]): F[String] = {
    val headers = r.headers.redactSensitive()
    def withBody(body: String) =
      show"Request: path- ${r.pathInfo} method-${r.method.name} uri-${r.uri.renderString} headers- $headers body-$body"
    writeMessageBodyString(r).map(withBody)
  }

  def responseLogger[F[_]: Effect](r: Response[F], l: Logger)(implicit ec: ExecutionContext): F[Response[F]] = {
    def forceBody(r: Response[F]): Boolean = !r.status.isEntityAllowed
    def cacheBody(r: Request[F]): Boolean = true

    logMessage(
      Kleisli(logRequest[F]),
      cacheBody,
      forceBody,
      _ => org.log4s.Trace,
      r,
      l
    )
  }

  def logResponse[F[_]: Sync](r: Response[F]): F[String] = {
    val headers = r.headers.redactSensitive()
    def withBody(body: String) =
      show"Response: status- ${r.status.code} headers-${r.headers} body-$body"
    writeMessageBodyString(r).map(withBody)
  }


  def writeMessageBodyString[F[_]: Sync](m: Message[F]) : F[String] = {
    if (isText(m)){
      m.bodyAsText(m.charset.getOrElse(Charset.`UTF-8`))
    } else {
      m.body.map(_.toHexString)
    }
  }.runFoldMonoidSync

  def isText[F[_]](message: Message[F]): Boolean = {
    val isBinary = message.contentType.exists(_.mediaType.binary)
    val isJson = message.contentType.exists(mT =>
      mT.mediaType == MediaType.`application/json` || mT.mediaType == MediaType.`application/hal+json`
    )

    !isBinary || isJson
  }



}
