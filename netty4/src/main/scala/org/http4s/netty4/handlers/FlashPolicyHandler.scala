//package org.http4s
//package netty4
//package handlers
//
//import org.jboss.netty.handler.codec.frame.FrameDecoder
//import org.jboss.netty.channel.{ChannelPipeline, ChannelFutureListener, Channel, ChannelHandlerContext}
//import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}
//import io.Codec
//
//object FlashPolicy {
//  private[this] def policyXml(domains: String = "*", ports: String = "*") =
//    <cross-domain-policy><allow-access-from domain={domains} to-ports={ports} /></cross-domain-policy>
//
//  val AllowAll = apply()
//
//  def apply(domains: Seq[String] = Seq.empty, ports: Seq[Int] = Seq.empty) = {
//    val doms = domains.mkString(",").blankOption getOrElse "*"
//    val prts = ports.mkString(",").blankOption getOrElse "*"
//    ChannelBuffers.copiedBuffer(policyXml(doms, prts).toString(), Codec.UTF8.charSet)
//  }
//}
//
///**
// * A flash policy handler for netty3. This needs to be included in the pipeline before anything else has touched
// * the message.
// *
// * @see [[https://github.com/cgbystrom/netty3-tools/blob/master/src/main/java/se/cgbystrom/netty3/FlashPolicyHandler.java]]
// * @param policyResponse The response xml to send for a request
// */
//class FlashPolicyHandler(policyResponse: ChannelBuffer = FlashPolicy.AllowAll) extends FrameDecoder {
//
//  def decode(ctx: ChannelHandlerContext, channel: Channel, buffer: ChannelBuffer) = {
//    if (buffer.readableBytes > 1) {
//
//      val magic1 = buffer.getUnsignedByte(buffer.readerIndex())
//      val magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1)
//      val isFlashPolicyRequest = (magic1 == '<' && magic2 == 'p')
//
//      if (isFlashPolicyRequest) {
//        // Discard everything
//        buffer.skipBytes(buffer.readableBytes())
//
//        // Make sure we don't have any downstream handlers interfering with our injected write of policy request.
//        removeAllPipelineHandlers(channel.getPipeline)
//        channel.write(policyResponse).addListener(ChannelFutureListener.CLOSE)
//        null
//      } else {
//
//        // Remove ourselves, important since the byte length check at top can hinder frame decoding
//        // down the pipeline
//        ctx.getPipeline.remove(this)
//        buffer.readBytes(buffer.readableBytes())
//      }
//    } else null
//  }
//
//  private def removeAllPipelineHandlers(pipe: ChannelPipeline) {
//    while (pipe.getFirst != null) {
//      pipe.removeFirst()
//    }
//  }
//}