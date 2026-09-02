/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.server

import cats.effect._
import org.http4s._
import org.http4s.implicits._

import scala.concurrent.duration._
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.{
  DefaultHttpRequest,
  HttpClientCodec,
  HttpClientUpgradeHandler,
  HttpHeaderNames,
  HttpMethod,
  HttpObject,
}
import io.netty.handler.codec.http2._

class EmberServerCancelSuite extends Http4sSuite {

  test("#6954 - request cancels if client close connection http2") {
    import org.http4s.dsl.io.*

    for {
      started <- Deferred[IO, Unit]
      cancelled <- Deferred[IO, Unit]

      app = HttpRoutes
        .of[IO] { case GET -> Root / "cancel" =>
          started.complete(()) *>
            IO.never.onCancel(cancelled.complete(()).void)
        }
        .orNotFound

      _ <- EmberServerBuilder
        .default[IO]
        .withHttp2
        .withHttpApp(app)
        .build
        .use { server =>
          nettyH2cUpgrade(server.address.getHostString, server.address.getPort).use { channel =>
            for {
              _ <- sendUpgradeRequest(
                channel
              )
              _ <- started.get.timeout(5.seconds)

              _ <- IO.blocking {
                channel.close().sync()
              }

              _ <- cancelled.get.timeout(5.seconds)
            } yield ()
          }
        }
    } yield ()
  }

  private def nettyH2cUpgrade(
      host: String,
      port: Int,
  ): Resource[IO, Channel] =
    Resource
      .make {
        IO.blocking {
          val group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory)

          val bootstrap =
            new Bootstrap()
              .group(group)
              .channel(classOf[NioSocketChannel])
              .handler(
                new ChannelInitializer[SocketChannel] {
                  override def initChannel(ch: SocketChannel): Unit = {
                    val sourceCodec =
                      new HttpClientCodec()

                    val http2FrameCodec =
                      Http2FrameCodecBuilder
                        .forClient()
                        .build()

                    val upgradeCodec =
                      new Http2ClientUpgradeCodec(
                        http2FrameCodec
                      )

                    val upgradeHandler =
                      new HttpClientUpgradeHandler(
                        sourceCodec,
                        upgradeCodec,
                        65536,
                      )

                    ch
                      .pipeline()
                      .addLast(
                        sourceCodec
                      ): Unit

                    ch
                      .pipeline()
                      .addLast(
                        upgradeHandler
                      ): Unit

                    ch
                      .pipeline()
                      .addLast(
                        "response",
                        new SimpleChannelInboundHandler[HttpObject] {

                          override def channelRead0(
                              ctx: ChannelHandlerContext,
                              msg: HttpObject,
                          ): Unit = {
                            ctx.fireChannelRead(msg): Unit
                            ()
                          }
                        },
                      ): Unit
                  }
                }
              )

          val channel =
            bootstrap
              .connect(host, port)
              .sync()
              .channel()

          (group, channel)
        }
      } { case (group, channel) =>
        IO.blocking {
          channel.close().sync(): Unit
          group.shutdownGracefully().sync(): Unit
          ()
        }.handleError(_ => ())
      }
      .map(_._2)

  private def sendUpgradeRequest(
      channel: Channel
  ): IO[Unit] =
    IO.blocking {
      val request =
        new DefaultHttpRequest(
          io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
          HttpMethod.GET,
          "/cancel",
        )

      request
        .headers()
        .set(
          HttpHeaderNames.CONNECTION,
          "Upgrade, HTTP2-Settings",
        )
        .set(
          HttpHeaderNames.UPGRADE,
          "h2c",
        )

      val _ = channel.writeAndFlush(request).sync()
      ()
    }
}
