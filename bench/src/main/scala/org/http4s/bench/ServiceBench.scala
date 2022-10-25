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

/** There are four implementations here:
  *
  * - function: a raw function Int => Resource[IO, Int]
  * - routes: like 0.23's HttpRoutes, but with a Resource
  * - altService: a Service monad with run as the SAM
  * - service: a proposed 1.0 monad, with applyOrElse as the SAM
  *
  * Each behaves thusly.  The rules were hacked together, and
  * are not particularly sensible in hindsight:
  *
  * - Each should increment the argument by 1.
  * - The first service is defined only at 42, returning -42
  * - The second service is defined for positive ints, and increments them
  * - All services default to 0
  */

@BenchmarkMode(Array(Mode.Throughput))
class ServiceBench {
  @Benchmark
  def functionFallback: Unit =
    ServiceBench.function(0).use_.unsafeRunSync()

  @Benchmark
  def functionDirect: Unit =
    ServiceBench.function(41).use_.unsafeRunSync()

  @Benchmark
  def functionFail: Unit =
    ServiceBench.function(-100).use_.unsafeRunSync()

  @Benchmark
  def routesFallback: Unit =
    ServiceBench.routes(0).use_.unsafeRunSync()

  @Benchmark
  def routesDirect: Unit =
    ServiceBench.routes(41).use_.unsafeRunSync()

  @Benchmark
  def routesFail: Unit =
    ServiceBench.routes(-100).use_.unsafeRunSync()

  @Benchmark
  def serviceFallback: Unit =
    ServiceBench.service(0).use_.unsafeRunSync()

  @Benchmark
  def serviceDirect: Unit =
    ServiceBench.service(41).use_.unsafeRunSync()

  @Benchmark
  def serviceFail: Unit =
    ServiceBench.service(-100).use_.unsafeRunSync()

  @Benchmark
  def altServiceFallback: Unit =
    ServiceBench.altService(0).use_.unsafeRunSync()

  @Benchmark
  def altServiceDirect: Unit =
    ServiceBench.altService(41).use_.unsafeRunSync()

  @Benchmark
  def altServiceFail: Unit =
    ServiceBench.altService(-100).use_.unsafeRunSync()
}

object ServiceBench {
  // This is HttpRoutes.of without the HTTP, but with Resource
  def routesOf[F[_]: Monad, A, B](
      pf: PartialFunction[A, Resource[F, B]]
  ): Kleisli[OptionT[Resource[F, *], *], A, B] =
    Kleisli(a => OptionT(Applicative[Resource[F, *]].unit >> pf.lift(a).sequence))

  // No Kleisli.
  val function: Int => Resource[IO, Int] = {
    val a: PartialFunction[Int, Resource[IO, Int]] = { case 42 =>
      Resource.pure(-42)
    }
    val b: PartialFunction[Int, Resource[IO, Int]] = {
      case i if i > 0 =>
        Resource.pure(i)
    }
    a.orElse(b) // cheats: can't do effectful routingv
      .compose[Int] { case i: Int =>
        i + 1
      }
      .andThen(_.flatMap(i => Resource.pure(i + 1)))
      .applyOrElse(_, Function.const(Resource.pure(0)))
  }

  val routes: Int => Resource[IO, Int] = {
    val a = routesOf[IO, Int, Int] { case 42 =>
      Resource.pure(-42)
    }
    val b = routesOf[IO, Int, Int] {
      case i if i > 0 =>
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
    val b = Service.of[IO, Int, Int] {
      case i if i > 0 =>
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
    val b = AltService.of[IO, Int, Int] {
      case i if i > 0 =>
        Resource.pure(i)
    }
    (a <+> b) // Combine two
      .local((i: Int) => i + 1) // transform the input
      .flatMap(i => AltService.pure(i + 1)) // transform the output
      .applyOrElse(_, Function.const(Resource.pure(0))) // "compiled" to the function
  }
}
