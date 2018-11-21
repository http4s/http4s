package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.Sync
import cats.implicits._
import com.example.http4s.blaze.demo.server.service.FileService
import org.http4s.EntityDecoder.multipart
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.multipart.Part

class MultipartHttpEndpoint[F[_]](fileService: FileService[F])(implicit F: Sync[F])
    extends Http4sDsl[F] {

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "multipart" =>
      Ok("Send a file (image, sound, etc) via POST Method")

    case req @ POST -> Root / ApiVersion / "multipart" =>
      req.decodeWith(multipart[F], strict = true) { response =>
        def filterFileTypes(part: Part[F]): Boolean =
          part.headers.exists(_.value.contains("filename"))

        val stream = response.parts.filter(filterFileTypes).traverse(fileService.store)

        Ok(stream.map(_ => s"Multipart file parsed successfully > ${response.parts}"))
      }
  }

}
