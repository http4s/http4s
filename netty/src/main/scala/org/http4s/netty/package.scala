package org.http4s

import org.jboss.netty.channel.{ChannelFutureListener, ChannelFuture, Channel}
import concurrent.{ExecutionContext, Promise, Future}
import util.{Failure, Success}
import scala.language.implicitConversions

package object netty {
  class Cancelled(val channel: Channel) extends Throwable
  class ChannelError(val channel: Channel, val reason: Throwable) extends Throwable
  private[netty] implicit def channelFuture2Future(cf: ChannelFuture)(implicit executionContext: ExecutionContext): Future[Channel] = {
    val prom = Promise[Channel]()
    cf.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          prom.complete(Success(future.getChannel))
        } else if (future.isCancelled) {
          prom.complete(Failure(new Cancelled(future.getChannel)))
        } else {
          prom.complete(Failure(new ChannelError(future.getChannel, future.getCause)))
        }
      }
    })
    prom.future
  }
}
