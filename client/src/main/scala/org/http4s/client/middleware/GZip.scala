/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect.{Bracket, Sync}
import fs2.{Pipe, Stream}
import java.io.EOFException
import org.http4s.headers.{`Accept-Encoding`, `Content-Encoding`}

/**
  * Client middleware for enabling gzip.
  */
object GZip {
  private val supportedCompressions =
    Seq(ContentCoding.gzip.coding, ContentCoding.deflate.coding).mkString(", ")

  def apply[F[_]](bufferSize: Int = 32 * 1024)(client: Client[F])(implicit F: Sync[F]): Client[F] =
    Client[F] { req =>
      val reqWithEncoding = addHeaders(req)
      val responseResource = client.run(reqWithEncoding)

      responseResource.map { actualResponse =>
        decompress(bufferSize, canEntityBodyBeEmpty(req.method), actualResponse)
      }
    }

  private def addHeaders[F[_]](req: Request[F]): Request[F] =
    req.headers.get(`Accept-Encoding`) match {
      case Some(_) =>
        req
      case _ =>
        req.withHeaders(
          req.headers ++ Headers.of(Header(`Accept-Encoding`.name.toString, supportedCompressions)))
    }

  private def canEntityBodyBeEmpty(requestMethod: Method): Boolean =
    requestMethod == Method.HEAD

  private def decompress[F[_]](
      bufferSize: Int,
      entityBodyCanBeEmpty: Boolean,
      response: Response[F])(implicit F: Sync[F]): Response[F] =
    response.headers.get(`Content-Encoding`) match {
      case Some(header)
          if header.contentCoding == ContentCoding.gzip || header.contentCoding == ContentCoding.`x-gzip` =>
        val gunzip: Pipe[F, Byte, Byte] =
          _.through(fs2.compression.gunzip(bufferSize)).flatMap(_.content)

        response.withBodyStream(response.body.through(decompressWith(gunzip, entityBodyCanBeEmpty)))

      case Some(header) if header.contentCoding == ContentCoding.deflate =>
        val deflate: Pipe[F, Byte, Byte] = fs2.compression.deflate(bufferSize)

        response.withBodyStream(
          response.body.through(decompressWith(deflate, entityBodyCanBeEmpty)))

      case _ =>
        response
    }

  private def decompressWith[F[_]](
      decompressor: Pipe[F, Byte, Byte],
      entityBodyCanBeEmpty: Boolean)(implicit F: Bracket[F, Throwable]): Pipe[F, Byte, Byte] =
    _.through(decompressor)
      .handleErrorWith {
        case _: EOFException if entityBodyCanBeEmpty => Stream.empty
        case error => Stream.raiseError(error)
      }
}
