package org.http4s.ember.client

import io.chrisdavenport.keypool._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client._
import cats._
import cats.implicits._
import cats.effect._
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import org.http4s.headers.Connection
import org.http4s.Response
import org.http4s.client._
import fs2.io.tcp.SocketGroup
import fs2.io.tcp.SocketOptionMapping
import scala.concurrent.duration.Duration

final class EmberClientBuilder[F[_]: Concurrent: Timer: ContextShift] private (
    private val sslContextOpt: Option[(ExecutionContext, SSLContext)],
    private val sgR: Resource[F, SocketGroup],
    val maxTotal: Int,
    val maxPerKey: RequestKey => Int,
    val idleTimeInPool: Duration,
    private val logger: Logger[F],
    val chunkSize: Int,
    val maxResponseHeaderSize: Int,
    val timeout: Duration,
    val additionalSocketOptions: List[SocketOptionMapping[_]]
) { self =>

  private def copy(
      sslContextOpt: Option[(ExecutionContext, SSLContext)] = self.sslContextOpt,
      sgR: Resource[F, SocketGroup] = self.sgR,
      maxTotal: Int = self.maxTotal,
      maxPerKey: RequestKey => Int = self.maxPerKey,
      idleTimeInPool: Duration = self.idleTimeInPool,
      logger: Logger[F] = self.logger,
      chunkSize: Int = self.chunkSize,
      maxResponseHeaderSize: Int = self.maxResponseHeaderSize,
      timeout: Duration = self.timeout,
      additionalSocketOptions: List[SocketOptionMapping[_]] = self.additionalSocketOptions
  ): EmberClientBuilder[F] = new EmberClientBuilder[F](
    sslContextOpt = sslContextOpt,
    sgR = sgR,
    maxTotal = maxTotal,
    maxPerKey = maxPerKey,
    idleTimeInPool = idleTimeInPool,
    logger = logger,
    chunkSize = chunkSize,
    maxResponseHeaderSize = maxResponseHeaderSize,
    timeout = timeout,
    additionalSocketOptions = additionalSocketOptions
  )

  def withSslContext(sslExecutionContext: ExecutionContext, sslContext: SSLContext) =
    copy(sslContextOpt = (sslExecutionContext, sslContext).some)
  def withoutSSLContext = copy(sslContextOpt = Option.empty[(ExecutionContext, SSLContext)])

  def withSocketGroup(sg: SocketGroup) = copy(sgR = sg.pure[Resource[F, ?]])

  def withMaxTotal(maxTotal: Int) = copy(maxTotal = maxTotal)
  def withMaxPerKey(maxPerKey: RequestKey => Int) = copy(maxPerKey = maxPerKey)
  def withIdleTimeInPool(idleTimeInPool: Duration) = copy(idleTimeInPool = idleTimeInPool)

  def withLogger(logger: Logger[F]) = copy(logger = logger)
  def withChunkSize(chunkSize: Int) = copy(chunkSize = chunkSize)
  def withMaxResponseHeaderSize(maxResponseHeaderSize: Int) =
    copy(maxResponseHeaderSize = maxResponseHeaderSize)
  def withTimeout(timeout: Duration) = copy(timeout = timeout)
  def withAdditionalSocketOptions(additionalSocketOptions: List[SocketOptionMapping[_]]) =
    copy(additionalSocketOptions = additionalSocketOptions)

  def build: Resource[F, Client[F]] =
    for {
      sg <- sgR
      builder = KeyPoolBuilder
        .apply[F, RequestKey, (RequestKeySocket[F], F[Unit])](
          { requestKey: RequestKey =>
            org.http4s.ember.client.internal.ClientHelpers
              .requestKeyToSocketWithKey[F](
                requestKey,
                sslContextOpt,
                sg,
                additionalSocketOptions
              )
              .allocated <* logger.trace(s"Created Connection - RequestKey: ${requestKey}")
          }, {
            case (RequestKeySocket(socket, r), shutdown) =>
              logger.trace(s"Shutting Down Connection - RequestKey: ${r}") >>
                socket.endOfInput.attempt.void >>
                socket.endOfOutput.attempt.void >>
                socket.close.attempt.void >>
                shutdown
          }
        )
        .withDefaultReuseState(Reusable.DontReuse)
        .withIdleTimeAllowedInPool(idleTimeInPool)
        .withMaxPerKey(maxPerKey)
        .withMaxTotal(maxTotal)
        .withOnReaperException(_ => Applicative[F].unit)
      pool <- builder.build
    } yield {
      val client = Client[F](request =>
        for {
          managed <- pool.take(RequestKey.fromRequest(request))
          _ <- Resource.liftF(
            pool.state.flatMap { poolState =>
              logger.trace(
                s"Connection Taken - Key: ${managed.value._1.requestKey} - Reused: ${managed.isReused} - PoolState: $poolState"
              )
            }
          )
          response <- Resource.make[F, Response[F]](
            org.http4s.ember.client.internal.ClientHelpers
              .request[F](
                request,
                managed.value._1,
                chunkSize,
                maxResponseHeaderSize,
                timeout
              )(logger)
              .map(response =>
                response.copy(body = response.body.onFinalizeCaseWeak {
                  case ExitCase.Completed =>
                    val requestClose = request.headers.get(Connection).exists(_.hasClose)
                    val responseClose = response.isChunked || response.headers
                      .get(Connection)
                      .exists(_.hasClose)

                    if (requestClose || responseClose) Sync[F].unit
                    else managed.canBeReused.set(Reusable.Reuse)
                  case ExitCase.Canceled => Sync[F].unit
                  case ExitCase.Error(_) => Sync[F].unit
                }))
          )(resp =>
            managed.canBeReused.get.flatMap {
              case Reusable.Reuse => resp.body.compile.drain.attempt.void
              case Reusable.DontReuse => Sync[F].unit
            })
        } yield response)
      new EmberClient[F](client, pool)
    }

}

object EmberClientBuilder {

  def default[F[_]: Concurrent: Timer: ContextShift] = new EmberClientBuilder[F](
    tryDefaultSslContext.map((ExecutionContext.global, _)),
    Defaults.socketGroup[F],
    Defaults.maxTotal,
    Defaults.maxPerKey,
    Defaults.idleTimeInPool,
    Slf4jLogger.getLogger[F],
    Defaults.chunkSize,
    Defaults.maxResponseHeaderSize,
    Defaults.timeout,
    Defaults.additionalSocketOptions
  )

  private object Defaults {
    def sslExecutionContext: ExecutionContext = ExecutionContext.global
    val acgFixedThreadPoolSize: Int = 100
    def socketGroup[F[_]: Sync: ContextShift]: Resource[F, SocketGroup] =
      Blocker[F].flatMap(b => SocketGroup[F](b))

    def sslContext[F[_]: Sync]: F[SSLContext] = Sync[F].delay(SSLContext.getDefault)
    val chunkSize: Int = 32 * 1024
    val maxResponseHeaderSize: Int = 4096
    val timeout: Duration = 60.seconds

    // Pool Settings
    val maxPerKey = { _: RequestKey =>
      100
    }
    val maxTotal = 100
    val idleTimeInPool = 30.seconds // 30 Seconds in Nanos
    val additionalSocketOptions = List.empty[SocketOptionMapping[_]]
  }

  private def tryDefaultSslContext: Option[SSLContext] =
    try Some(SSLContext.getDefault())
    catch {
      case scala.util.control.NonFatal(_) => None
    }

}
