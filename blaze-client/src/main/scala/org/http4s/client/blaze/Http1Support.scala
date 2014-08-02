package org.http4s.client.blaze

import java.net.InetSocketAddress

import org.http4s.Request
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.util.CaseInsensitiveString._

import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/, \/-}

trait Http1Support extends PipelineBuilder {

  type AddressResult = \/[Throwable, InetSocketAddress]

  implicit protected def ec: ExecutionContext

  override protected def buildPipeline(req: Request, closeOnFinish: Boolean): PipelineResult = {
    val isHttp = req.requestUri.scheme match {
      case Some(s) if s != "http".ci => false
      case _ => true
    }

    if (isHttp && req.requestUri.authority.isDefined) {
      val t = new Http1ClientStage()
      PipelineResult(LeafBuilder(t), t)
    }
    else super.buildPipeline(req, closeOnFinish)
  }

  override protected def getAddress(req: Request): AddressResult = {
    req.requestUri
     .authority
     .fold[AddressResult](-\/(new Exception("Request must have an authority"))){ auth =>
      val port = auth.port.getOrElse(80)
      \/-(new InetSocketAddress(auth.host.value, port))
    }
  }
}
