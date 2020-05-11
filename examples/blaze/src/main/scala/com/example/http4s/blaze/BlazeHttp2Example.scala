/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.http4s
package blaze

import cats.effect._

object BlazeHttp2Example extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslExampleApp.builder[IO].flatMap(_.enableHttp2(true).serve.compile.lastOrError)
}
