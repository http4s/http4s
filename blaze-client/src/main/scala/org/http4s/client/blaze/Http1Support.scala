package org.http4s.client.blaze

import java.net.InetSocketAddress

import org.http4s.Request
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.util.CaseInsensitiveString._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scalaz.{-\/, \/, \/-}

trait Http1Support extends PipelineBuilder {

  type AddressResult = \/[Throwable, InetSocketAddress]

  implicit protected def ec: ExecutionContext

  override protected def buildPipeline(req: Request, closeOnFinish: Boolean): PipelineResult = {
    val isHttp = req.uri.scheme match {
      case Some(s) if s != "http".ci => false
      case _ => true
    }

    if (isHttp && req.uri.authority.isDefined) {
      val t = new Http1ClientStage(timeout)
      PipelineResult(LeafBuilder(t), t)
    }
    else super.buildPipeline(req, closeOnFinish)
  }

  override protected def getAddress(req: Request): AddressResult = {
    req.uri
     .authority
     .fold[AddressResult](-\/(new Exception("Request must have an authority"))){ auth =>
      val port = auth.port.getOrElse(80)
      \/-(new InetSocketAddress(auth.host.value, port))
    }
  }
}
