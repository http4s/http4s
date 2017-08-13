package org.http4s
package server
package staticcontent

import java.io.File

import cats.data._
import cats.implicits._
import fs2._
import fs2.interop.cats._
import org.http4s.headers.Range.SubRange
import org.http4s.headers._

import scala.concurrent.ExecutionContext

object FileService {

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param pathPrefix prefix of Uri from which content will be served
    * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param bufferSize buffer size to use for internal read buffers
    * @param executionContext `ExecutionContext` to use when collecting content
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config(systemPath: String,
                          pathPrefix: String = "",
                          pathCollector: (File, Config, Request) => OptionT[Task, Response] = filesOnly,
                          bufferSize: Int = 50*1024,
                          executionContext: ExecutionContext= ExecutionContext.global,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy)

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): HttpService = Service.lift { req =>
    val uriPath = req.pathInfo
    if (!uriPath.startsWith(config.pathPrefix))
      Pass.now
    else
      getFile(config.systemPath + '/' + getSubPath(uriPath, config.pathPrefix))
        .flatMap { f => config.pathCollector(f, config, req) }
        .orElse(OptionT.none[Task, Response])
        .fold(Pass.now)(config.cacheStrategy.cache(uriPath, _))
        .flatten
  }

  /* Returns responses for static files.
   * Directories are forbidden.
   */
  private def filesOnly(file: File, config: Config, req: Request): OptionT[Task, Response] = OptionT(Task.delay(
    if (file.isDirectory) Task.now(Some(Response(Status.Unauthorized)))
    else if (!file.isFile) Task.now(None)
    else (getPartialContentFile(file, config, req) orElse
      StaticFile.fromFile(file, config.bufferSize, Some(req))
        .map(_.putHeaders(AcceptRangeHeader))).value
  ).flatten)

  private def validRange(start: Long, end: Option[Long], fileLength: Long): Boolean = {
    start < fileLength && (end match {
      case Some(end) => start >= 0 && start <= end
      case None      => start >= 0 || fileLength + start - 1 >= 0
    })
  }

  // Attempt to find a Range header and collect only the subrange of content requested
  private def getPartialContentFile(file: File, config: Config, req: Request): OptionT[Task, Response] = OptionT.fromOption[Task](req.headers.get(Range)).flatMap {
    case Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil)) if validRange(s, e, file.length) => OptionT(Task.delay {
      val size = file.length()
      val start = if (s >= 0) s else math.max(0, size + s)
      val end = math.min(size - 1, e getOrElse (size - 1))  // end is inclusive

      StaticFile .fromFile(file, start, end + 1, config.bufferSize, Some(req))
                  .map { resp =>
                    val hs = resp.headers.put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
                    resp.copy(status = Status.PartialContent, headers = hs)
                  }.value}.flatten)

    case _ => OptionT.none
  }

  // Attempts to sanitize the file location and retrieve the file. Returns None if the file doesn't exist.
  private def getFile(unsafePath: String): OptionT[Task, File] = OptionT(Task.delay {
    val f = new File(sanitize(unsafePath))
    if (f.exists()) Some(f)
    else None
  })
}
