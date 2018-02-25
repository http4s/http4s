package com.example.http4s.blaze.demo.server

import cats.effect.Effect
import cats.syntax.semigroupk._ // For <+>
import com.example.http4s.blaze.demo.server.endpoints._
import com.example.http4s.blaze.demo.server.endpoints.auth.{BasicAuthHttpEndpoint, GitHubHttpEndpoint}
import com.example.http4s.blaze.demo.server.service.{FileService, GitHubService}
import fs2.Scheduler
import org.http4s.HttpService
import org.http4s.client.Client
import org.http4s.server.HttpMiddleware
import org.http4s.server.middleware.{AutoSlash, ChunkAggregator, GZip, Timeout}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Module[F[_]](client: Client[F])(implicit F: Effect[F], S: Scheduler) {

  private val fileService = new FileService[F]

  private val gitHubService = new GitHubService[F](client)

  def middleware: HttpMiddleware[F] = {
    {(service: HttpService[F]) => GZip(service)(F)} compose
      { service => AutoSlash(service)(F) }
  }

  val fileHttpEndpoint: HttpService[F] =
    new FileHttpEndpoint[F](fileService).service

  val nonStreamFileHttpEndpoint = ChunkAggregator(fileHttpEndpoint)

  private val hexNameHttpEndpoint: HttpService[F] =
    new HexNameHttpEndpoint[F].service

  private val compressedEndpoints: HttpService[F] =
    middleware(hexNameHttpEndpoint)

  private val timeoutHttpEndpoint: HttpService[F] =
    new TimeoutHttpEndpoint[F].service

  private val timeoutEndpoints: HttpService[F] =
    Timeout(1.second)(timeoutHttpEndpoint)

  private val mediaHttpEndpoint: HttpService[F] =
    new JsonXmlHttpEndpoint[F].service

  private val multipartHttpEndpoint: HttpService[F] =
    new MultipartHttpEndpoint[F](fileService).service

  private val gitHubHttpEndpoint: HttpService[F] =
    new GitHubHttpEndpoint[F](gitHubService).service

  val basicAuthHttpEndpoint: HttpService[F] =
    new BasicAuthHttpEndpoint[F].service

  // NOTE: If you mix services wrapped in `AuthMiddleware[F, ?]` the entire namespace will be protected.
  // You'll get 401 (Unauthorized) instead of 404 (Not found). Mount it separately as done in Server.
  val httpServices: HttpService[F] = (
    compressedEndpoints <+> timeoutEndpoints
    <+> mediaHttpEndpoint <+> multipartHttpEndpoint
    <+> gitHubHttpEndpoint
  )

}
