package org.http4s.client.blaze

import java.io.IOException
import java.nio.ByteBuffer

import org.http4s.Request
import org.http4s.blaze.pipeline.LeafBuilder

import scala.concurrent.duration.Duration
import scalaz.-\/

trait PipelineBuilder {

  protected case class PipelineResult(builder: LeafBuilder[ByteBuffer], tail: BlazeClientStage)

  /** Specify the timeout for the entire request */
  protected def timeout: Duration

  /** Generate the pipeline for the [[Request]] */
  protected def buildPipeline(req: Request, closeOnFinish: Boolean): PipelineResult = {
    sys.error(s"Unsupported request: ${req.uri}")
  }

  /** Find the address from the [[Request]] */
  protected def getAddress(req: Request): AddressResult =
    -\/(new IOException(s"Unable to generate address from request: $req"))
}

