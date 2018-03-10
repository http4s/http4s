package org.http4s.client.asynchttpclient

import io.netty.buffer.ByteBuf
import org.asynchttpclient.request.body.Body
import org.asynchttpclient.request.body.generator.BodyGenerator

private[asynchttpclient] object EmptyBodyGenerator extends BodyGenerator {
  override val createBody: Body = new Body {
    override val getContentLength: Long = 0L
    override def transferTo(target: ByteBuf): Body.BodyState = Body.BodyState.STOP
    override def close() = {}
  }
}
