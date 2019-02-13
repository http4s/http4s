package org.http4s.ember.server.internal

import fs2._
import fs2.concurrent._
import fs2.io.tcp
import fs2.io.tcp._
import cats.effect._
import cats.implicits._
import scala.concurrent.duration._
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import org.http4s._
import org.http4s.ember.core.{Encoder, Parser}
import org.http4s.ember.core.Util.readWithTimeout

object ServerHelpers {
  def server[F[_]: ConcurrentEffect : ContextShift](
    bindAddress: InetSocketAddress,
    httpApp: HttpApp[F],
    ag: AsynchronousChannelGroup,
    // Defaults
    onError: Throwable => Response[F] = {_: Throwable => Response[F](Status.InternalServerError)},
    onWriteFailure : Option[(Option[Request[F]], Response[F], Throwable) => F[Unit]] = None,
    terminationSignal: Option[SignallingRef[F, Boolean]] = None,
    maxConcurrency: Int = Int.MaxValue,
    receiveBufferSize: Int = 256 * 1024,
    maxHeaderSize: Int = 10 * 1024,
    requestHeaderReceiveTimeout: Duration = 5.seconds
  )(implicit C: Clock[F]): Stream[F, Nothing] = {
    implicit val AG = ag

    // Termination Signal, if not present then does not terminate.
    val termSignal: F[SignallingRef[F, Boolean]] = 
      terminationSignal.fold(SignallingRef[F, Boolean](false))(_.pure[F])

    val writeFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit] = {
      // Jesus Scalac
      def doNothing(f: Option[Request[F]], r: Response[F], t: Throwable) : F[Unit] = {
        val _ = {
          val _ = {
            val _ = t
            r
          }
          f
        }
        Sync[F].unit
      }
        
      onWriteFailure match {
        case Some(f) => f
        case None => doNothing
      }
    }
    
    def socketReadRequest(
      socket: Socket[F], 
      requestHeaderReceiveTimeout: Duration, 
      receiveBufferSize: Int): F[Request[F]] = {
        val (initial, readDuration) = requestHeaderReceiveTimeout match {
          case fin: FiniteDuration => (true, fin)
          case _ => (false, 0.millis)
        }
        SignallingRef[F, Boolean](initial).flatMap{timeoutSignal =>
        C.realTime(MILLISECONDS).flatMap(now => 
          Parser.Request.parser(maxHeaderSize)(
            readWithTimeout[F](socket, now, readDuration, timeoutSignal.get, receiveBufferSize)
          )
            .flatMap{req => 
              // Sync[F].delay(logger.debug(s"Request Processed $req")) *>
              timeoutSignal.set(false).as(req)
            }
        )}
      }

    Stream.eval(termSignal).flatMap(terminationSignal => 
    tcp.Socket.server[F](bindAddress)
      .map(connect => 
        Stream.eval(
          connect.use{socket =>
            val app: F[(Request[F], Response[F])] = for {
              req <- socketReadRequest(socket, requestHeaderReceiveTimeout, receiveBufferSize)
              resp <- httpApp.run(req)
                .handleError(onError)
                // .flatTap(resp => Sync[F].delay(logger.debug(s"Response Created $resp")))
            } yield (req, resp)
            def send(request:Option[Request[F]], resp: Response[F]): F[Unit] = {
              Stream(resp)
                .covary[F]
                .flatMap(Encoder.respToBytes[F])
                .through(socket.writes())
                .compile
                .drain
                .attempt
                .flatMap{
                  case Left(err) => writeFailure(request, resp, err)
                  case Right(()) => Sync[F].pure(())
                }
            }
            app.attempt.flatMap{
              case Right((request, response)) => send(Some(request), response)
              case Left(err) => send(None, onError(err))
            }
          }
        )
      ).parJoin(maxConcurrency)
        .interruptWhen(terminationSignal)
        .drain
    )
  }
}