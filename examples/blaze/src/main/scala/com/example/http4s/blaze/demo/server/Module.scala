package com.example.http4s.blaze.demo.server

import cats.data.OptionT
import cats.effect._
import cats.syntax.semigroupk._ // For <+>
import com.example.http4s.blaze.demo.server.endpoints._
import com.example.http4s.blaze.demo.server.endpoints.auth.{
  BasicAuthHttpEndpoint,
  GitHubHttpEndpoint
}
import com.example.http4s.blaze.demo.server.service.{FileService, GitHubService}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.server.HttpMiddleware
import org.http4s.server.middleware.{AutoSlash, ChunkAggregator, GZip, Timeout}

import scala.concurrent.duration._

class Module[F[_]](client: Client[F])(
    implicit F: ConcurrentEffect[F],
    CS: ContextShift[F],
    T: Timer[F]) {

  private val fileService = new FileService[F]

  private val gitHubService = new GitHubService[F](client)

  def middleware: HttpMiddleware[F] = { routes: HttpRoutes[F] =>
    GZip(routes)
  }.compose(routes => AutoSlash(routes))

  val fileHttpEndpoint: HttpRoutes[F] =
    new FileHttpEndpoint[F](fileService).service

  val nonStreamFileHttpEndpoint: HttpRoutes[F] =
    ChunkAggregator(OptionT.liftK[F])(fileHttpEndpoint)

  private val hexNameHttpEndpoint: HttpRoutes[F] =
    new HexNameHttpEndpoint[F].service

  private val compressedEndpoints: HttpRoutes[F] =
    middleware(hexNameHttpEndpoint)

  private val timeoutHttpEndpoint: HttpRoutes[F] =
    new TimeoutHttpEndpoint[F].service

  private val timeoutEndpoints: HttpRoutes[F] =
    Timeout(1.second)(timeoutHttpEndpoint)

  private val mediaHttpEndpoint: HttpRoutes[F] =
    new JsonXmlHttpEndpoint[F].service

  private val multipartHttpEndpoint: HttpRoutes[F] =
    new MultipartHttpEndpoint[F](fileService).service

  private val gitHubHttpEndpoint: HttpRoutes[F] =
    new GitHubHttpEndpoint[F](gitHubService).service

  val basicAuthHttpEndpoint: HttpRoutes[F] =
    new BasicAuthHttpEndpoint[F].service

  val httpServices: HttpRoutes[F] = (
    compressedEndpoints <+> timeoutEndpoints
      <+> mediaHttpEndpoint <+> multipartHttpEndpoint
      <+> gitHubHttpEndpoint
  )

}
