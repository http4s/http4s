package org.http4s
package server
package staticcontent

import java.io.File
import java.util.concurrent.ExecutorService

import cats.data.{NonEmptyList, OneAnd}
import fs2._
import org.http4s.headers._
import org.http4s.headers.Range.SubRange

object FileService {

  /** [[org.http4s.server.staticcontent.FileService]] configuration
    *
    * @param systemPath path prefix to the folder from which content will be served
    * @param pathPrefix prefix of Uri from which content will be served
    * @param pathCollector function that performs the work of collecting the file or rendering the directory into a response.
    * @param bufferSize buffer size to use for internal read buffers
    * @param executor `ExecutorService` to use when collecting content
    * @param cacheStrategy strategy to use for caching purposes. Default to no caching.
    */
  final case class Config(systemPath: String,
                          pathPrefix: String = "",
                          pathCollector: (File, Config, Request) => Task[Option[Response]] = filesOnly,
                          bufferSize: Int = 50*1024,
                          executor: ExecutorService,
                          cacheStrategy: CacheStrategy = NoopCacheStrategy)

  /** Make a new [[org.http4s.HttpService]] that serves static files. */
  private[staticcontent] def apply(config: Config): HttpService = Service.lift { req =>
    val uriPath = req.pathInfo
    if (!uriPath.startsWith(config.pathPrefix))
      Pass.now
    else
      getFile(config.systemPath + '/' + getSubPath(uriPath, config.pathPrefix))
        .map { f => config.pathCollector(f, config, req) }
        .getOrElse(Task.now(None))
        .flatMap(_.fold(Pass.now)(config.cacheStrategy.cache(uriPath, _)))
  }

  /* Returns responses for static files.
   * Directories are forbidden.
   */
  private def filesOnly(file: File, config: Config, req: Request): Task[Option[Response]] = Task.now {
    if (file.isDirectory()) Some(Response(Status.Unauthorized))
    else if (!file.isFile) None
    else getPartialContentFile(file, config, req) orElse
      StaticFile.fromFile(file, config.bufferSize, Some(req))(config.executor)
                .map(_.putHeaders(AcceptRangeHeader))
  }

  private def validRange(start: Long, end: Option[Long], fileLength: Long): Boolean = {
    start < fileLength && (end match {
      case Some(end) => start >= 0 && start <= end
      case None      => start >= 0 || fileLength + start - 1 >= 0
    })
  }

  // Attempt to find a Range header and collect only the subrange of content requested
  private def getPartialContentFile(file: File, config: Config, req: Request): Option[Response] = req.headers.get(Range).flatMap {
    case Range(RangeUnit.Bytes, NonEmptyList(SubRange(s, e), Nil)) if validRange(s, e, file.length) =>
      val size = file.length()
      val start = if (s >= 0) s else math.max(0, size + s)
      val end = math.min(size - 1, e getOrElse (size - 1))  // end is inclusive

      StaticFile .fromFile(file, start, end + 1, config.bufferSize, Some(req))(config.executor)
                  .map { resp =>
                    val hs = resp.headers.put(AcceptRangeHeader, `Content-Range`(SubRange(start, end), Some(size)))
                    resp.copy(status = Status.PartialContent, headers = hs)
                  }

    case _ => None
  }

  // Attempts to sanitize the file location and retrieve the file. Returns None if the file doesn't exist.
  private def getFile(unsafePath: String): Option[File] = {
    val f = new File(sanitize(unsafePath))
    if (f.exists()) Some(f)
    else None
  }
}
