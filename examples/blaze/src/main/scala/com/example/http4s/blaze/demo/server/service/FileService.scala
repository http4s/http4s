/*
 * Copyright 2013 http4s.org
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

package com.example.http4s.blaze.demo.server.service

import java.io.File
import java.nio.file.Paths
import cats.effect.{Blocker, ContextShift, Effect}
import com.example.http4s.blaze.demo.StreamUtils
import fs2.Stream
import org.http4s.multipart.Part

class FileService[F[_]: ContextShift](blocker: Blocker)(implicit F: Effect[F], S: StreamUtils[F]) {
  def homeDirectories(depth: Option[Int]): Stream[F, String] =
    S.env("HOME").flatMap { maybePath =>
      val ifEmpty = S.error("HOME environment variable not found!")
      maybePath.fold(ifEmpty)(directories(_, depth.getOrElse(1)))
    }

  def directories(path: String, depth: Int): Stream[F, String] = {
    def dir(f: File, d: Int): Stream[F, File] = {
      val dirs = Stream.emits(f.listFiles().toSeq).filter(_.isDirectory).covary[F]

      if (d <= 0) Stream.empty
      else if (d == 1) dirs
      else dirs ++ dirs.flatMap(x => dir(x, d - 1))
    }

    S.evalF(new File(path)).flatMap { file =>
      dir(file, depth)
        .map(_.getName)
        .filter(!_.startsWith("."))
        .intersperse("\n")
    }
  }

  def store(part: Part[F]): Stream[F, Unit] =
    for {
      home <- S.evalF(sys.env.getOrElse("HOME", "/tmp"))
      filename <- S.evalF(part.filename.getOrElse("sample"))
      path <- S.evalF(Paths.get(s"$home/$filename"))
      _ <- part.body.through(fs2.io.file.writeAll(path, blocker))
    } yield ()
}
