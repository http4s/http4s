/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.server

import cats.effect._
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.syntax.all._
import org.http4s.server.Server

import java.net.URI
import scala.annotation.nowarn
import scala.scalajs.js

trait EmberServerWebSocketSuitePlatform { self: EmberServerWebSocketSuite =>

  def serverURI(server: Server, path: String): URI =
    URI.create(s"ws://${server.address.host}:${server.address.port}/$path")

  val ws = js.Dynamic.global.require("ws")

  case class Client(
      waitOpen: Deferred[IO, Unit],
      waitClose: Deferred[IO, Unit],
      error: Deferred[IO, Throwable],
      messages: Queue[IO, String],
      pongs: Queue[IO, String],
      client: js.Dynamic) {
    def connect: IO[Unit] = error.get.race(waitOpen.get).rethrow
    def close: IO[Unit] = IO(client.close(1000)) >> error.get.race(waitClose.get).rethrow
    def send(msg: String): IO[Unit] =
      error.get.race(IO.async_[Unit](cb => client.send(msg, () => cb(Right(()))): @nowarn)).rethrow
    def ping(data: String): IO[Unit] = error.get.race(IO(client.ping(data)).void).rethrow
    def remoteClosed = waitClose
  }

  def createClient(target: URI, dispatcher: Dispatcher[IO]): IO[Client] =
    for {
      waitOpen <- Deferred[IO, Unit]
      waitClose <- Deferred[IO, Unit]
      error <- Deferred[IO, Throwable]
      queue <- Queue.unbounded[IO, String]
      pongQueue <- Queue.unbounded[IO, String]
      client <- IO {
        val websocket = js.Dynamic.newInstance(ws)(target.toString())
        websocket.on("open", () => dispatcher.unsafeRunAndForget(waitOpen.complete(())))
        websocket.on("close", () => dispatcher.unsafeRunAndForget(waitClose.complete(())))
        websocket.on(
          "error",
          (e: js.Any) => dispatcher.unsafeRunAndForget(error.complete(js.JavaScriptException(e))))
        websocket.on(
          "message",
          (buffer: js.Dynamic) =>
            dispatcher.unsafeRunAndForget(queue.offer(buffer.toString.asInstanceOf[String])))
        websocket.on(
          "pong",
          (buffer: js.Dynamic) =>
            dispatcher.unsafeRunAndForget(pongQueue.offer(buffer.toString.asInstanceOf[String])))
      }
    } yield Client(waitOpen, waitClose, error, queue, pongQueue, client)

}
