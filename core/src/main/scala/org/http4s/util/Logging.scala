package org.http4s.util

import cats.implicits._
import cats.effect.{Effect, Sync}
import org.http4s._
//import org.http4s.Method._
import org.log4s.Logger
import org.log4s.LogLevel
import fs2._

import scala.concurrent.ExecutionContext

object Logging {

//  trait Logger[F[_], A <: Message[F]] {
//    // If this is false using anything from the body will cause the program to fail
//    def cacheBody: Boolean
//    def messageString(implicit F: Sync[F]): A => F[String]
//
//    final def logEvent(implicit F: Sync[F]) = logger.
//
//    final def logEffect(a: A)(implicit F: Effect[F]): F[A] = {
//      if (cacheBody){
//        for {
//          queue <- fs2.async.unboundedQueue[F, Byte]
//          complete <- F.delay(a.body
//            .observe(queue.enqueue)
//              .onFinalize(
//                logRequest(a.withBodyStream(
//                  queue.size.get.flatMap(s => queue.dequeue.take(s))
//                ))
//              ))
//        } yield ()
//      }
//      else logEffect(a).as(a)
//    }
//  }
//
//  object Logger {
//
//    def apply[F[_], A](cacheBody: Boolean, logMessage: A => String): Logger[F, A] = new Logger[F, A] {
//      override def cacheBody: Boolean = cacheBody
//      override def logRequest: A => String = logMessage
//    }
//
//  }

  def logMessage[F[_], A <: Message[F]](
                                         f: A => F[String],
                                         a: A,
                                         logger: Logger,
                                         logLevel: LogLevel
                                       )(implicit F: Sync[F]): F[Unit] = {
    f(a).flatMap(s => F.delay(logger(logLevel)(s)))
  }

  def logRequest[F[_]](
                        f: Request[F] => F[String],
                        cacheBody: Boolean = true,
                        r: Request[F],
                        logger: Logger,
                        logLevel: LogLevel
                      )(implicit F: Effect[F], ec: ExecutionContext): F[Request[F]] = {
    r.method match {
      case _ if !cacheBody =>
        logMessage(f,r,logger, logLevel).as(r)
      case _ =>
        for {
          bodyVec <- fs2.async.refOf(Vector.empty[Byte])
          concealedRequest <- F.delay{
            val newBody = Stream.eval(bodyVec.get).flatMap(v => Stream.emits(v).covary[F])
            val loggedRequest = r.withBodyStream(newBody)
            r.withBodyStream(
              r.body
                .observe(_.chunks.flatMap(c => Stream.eval_(bodyVec.modify(_ ++ c.toVector))))
                .onFinalize(logMessage(f, loggedRequest, logger, logLevel))
            )
          }
        } yield concealedRequest
    }
  }

}
