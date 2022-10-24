/*
 * Copyright 2015 http4s.org
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

package org.http4s
package bench

import cats.Applicative
import cats.Monad
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.unsafe.implicits.global
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

// sbt "bench/jmh:run -i 10 -wi 10 -f 2 -t 1 org.http4s.bench.ServiceBench"
@BenchmarkMode(Array(Mode.Throughput))
class ServiceBench {
  @Benchmark
  def routes: Unit =
    ServiceBench.routes(0).use_.unsafeRunSync()

  @Benchmark
  def service: Unit =
    ServiceBench.service(0).use_.unsafeRunSync()

  @Benchmark
  def altService: Unit =
    ServiceBench.altService(0).use_.unsafeRunSync()
}

object ServiceBench {
  // This is HttpRoutes.of without the HTTP, but with Resource
  def routesOf[F[_]: Monad, A, B](
      pf: PartialFunction[A, Resource[F, B]]
  ): Kleisli[OptionT[Resource[F, *], *], A, B] =
    Kleisli(a => OptionT(Applicative[Resource[F, *]].unit >> pf.lift(a).sequence))

  val routes: Int => Resource[IO, Int] = {
    val a = routesOf[IO, Int, Int] { case 42 =>
      Resource.pure(-42)
    }
    val b = routesOf[IO, Int, Int] { case i =>
      Resource.pure(i)
    }
    (a <+> b) // Combine two
      .local((i: Int) => i + 1) // transform the input
      .flatMap(i => Kleisli.pure(i + 1)) // transform the output
      .mapF(_.getOrElse(0)) // eliminate the OptionT
      .run // "compiled" to the function
  }

  val service: Int => Resource[IO, Int] = {
    val a = Service.of[IO, Int, Int] { case 42 =>
      Resource.pure(-42)
    }
    val b = Service.of[IO, Int, Int] { case i =>
      Resource.pure(i)
    }
    (a <+> b) // Combine two
      .local((i: Int) => i + 1) // transform the input
      .flatMap(i => Service.pure(i + 1)) // transform the output
      .applyOrElse(_, Function.const(Resource.pure(0))) // "compiled" to the function
  }

  // Forked from https://github.com/http4s/http4s/blob/7384fc676f864bd8403d38472ecb9deda83d4523/core/shared/src/main/scala/org/http4s/Service.scala to test an alternate encoding
  val altService: Int => Resource[IO, Int] = {
    val a = AltService.of[IO, Int, Int] { case 42 =>
      Resource.pure(-42)
    }
    val b = AltService.of[IO, Int, Int] { case i =>
      Resource.pure(i)
    }
    (a <+> b) // Combine two
      .local((i: Int) => i + 1) // transform the input
      .flatMap(i => AltService.pure(i + 1)) // transform the output
      .runOrElse(0) // "compiled" to the function
  }
}
