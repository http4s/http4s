//package org.http4s
//package netty
//
//import cats.effect.IO
//import cats.syntax.all._
//import io.netty.util.ResourceLeakDetector
//import org.http4s.client.blaze._
//import org.http4s.dsl.io._
//import org.specs2.specification.AfterAll
//
//
//class NettyIntegrationTests extends Http4sSpec with AfterAll {
//
//  val server = NettyBuilder[IO].start.unsafeRunSync()
//
//  val client = Http1Client[IO]().unsafeRunSync()
//
//  //Panic on a bytebuf not cleaned up. It won't give us a test error,
//  //but it will essentially
//  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)
//
//
//
//  val service: HttpService[IO] = HttpService {
//    case GET -> Root / "ping" =>
//      Ok()
//    case r @ POST -> Root / "consume" =>
//      r.body.compile.drain >> Ok()
//    case GET -> Root / "simple" =>
//      Ok("waylonnn jenningssss")
//    case HEAD -> Root / "head" =>
//      Ok("LOLOLOL")
//    case
//  }
//
//  "Http4s Netty server integration test" should {
//
//
//
//  }
//
//
//  override def afterAll(): Unit = {
//    server.shutdown.attempt.unsafeRunSync()
//    client.shutdown.attempt.unsafeRunSync()
//    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED)
//    ()
//  }
//}
