package org.http4s.ember.server

import cats.effect.Async
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.comcast.ip4s.SocketAddress
import com.comcast.ip4s.IpAddress
import org.http4s.server.Server
import java.net.InetSocketAddress

private[server] trait EmberServerBuilderCompanionPlatform {

  private[server] def defaultLogger[F[_]: Async]: Logger[F] = Slf4jLogger.getLogger[F]
  
  private[server] def mkServer(bindAddress: SocketAddress[IpAddress], secure: Boolean): Server =
    new Server {
      val address = bindAddress.toInetSocketAddress
      val isSecure = secure
    }

}
