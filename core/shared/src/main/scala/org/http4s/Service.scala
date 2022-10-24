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

package org.http4s

import cats.Alternative
import cats.Applicative
import cats.Monad
import cats.StackSafeMonad
import cats.effect.kernel.Resource
import cats.mtl.Local

import Service._

sealed abstract class Service[F[_], A, B] { self =>
  def applyOrElse(a: A, default: A => Resource[F, B]): Resource[F, B]

  def map[C](f: B => C): Service[F, A, C] =
    new Service[F, A, C] {
      def applyOrElse(a: A, default: A => Resource[F, C]): Resource[F, C] = {
        val rb = self.applyOrElse(a, checkFallback)
        if (isFallback(rb)) default(a) else rb.map(f)
      }
    }

  def flatMap[C](f: B => Service[F, A, C]) =
    new Service[F, A, C] {
      def applyOrElse(a: A, default: A => Resource[F, C]): Resource[F, C] = {
        val rb = self.applyOrElse(a, checkFallback)
        if (isFallback(rb)) default(a) else rb.flatMap(f(_).applyOrElse(a, default))
      }
    }

  def orElse(default: => Service[F, A, B]): Service[F, A, B] =
    new Service[F, A, B] {
      def applyOrElse(a: A, default2: A => Resource[F, B]): Resource[F, B] = {
        val rb = self.applyOrElse(a, checkFallback)
        if (isFallback(rb)) default.applyOrElse(a, default2)
        else rb
      }
    }

  def local[C](f: C => A): Service[F, C, B] =
    new Service[F, C, B] {
      def applyOrElse(c: C, default: C => Resource[F, B]): Resource[F, B] = {
        val rb = self.applyOrElse(f(c), checkFallback)
        if (isFallback(rb)) default(c) else rb
      }
    }
}

object Service extends ServiceInstances {
  // This is the same basic trick as used in scala.PartialFunction to
  // detect partiality without allocating an option.
  private[this] object Fallback
  private[this] val fallbackResource = Resource.pure[Any, Any](Fallback)
  private[this] val fallbackFunction: Any => AnyRef = Function.const(fallbackResource)
  private def checkFallback[F[_], B] = fallbackFunction.asInstanceOf[Any => Resource[F, B]]
  private def isFallback[F[_], B](x: Resource[F, B]) = fallbackResource eq x.asInstanceOf[AnyRef]

  def pure[F[_], A, B](b: B): Service[F, A, B] =
    new Service[F, A, B] {
      val rb = Resource.pure[F, B](b)
      def applyOrElse(a: A, default: A => Resource[F, B]): Resource[F, B] =
        rb
    }

  def pass[F[_], A, B]: Service[F, A, B] =
    new Service[F, A, B] {
      def applyOrElse(a: A, default: A => Resource[F, B]): Resource[F, B] =
        default(a)
    }

  def ask[F[_], A]: Service[F, A, A] =
    new Service[F, A, A] {
      def applyOrElse(a: A, default: A => Resource[F, A]): Resource[F, A] =
        Resource.pure(a)
    }

  def of[F[_], A, B](pf: PartialFunction[A, Resource[F, B]]): Service[F, A, B] =
    new Service[F, A, B] {
      def applyOrElse(a: A, default: A => Resource[F, B]): Resource[F, B] =
        pf.applyOrElse(a, default)
    }
}

private[http4s] sealed abstract class ServiceInstance[F[_], A]
    extends Monad[Service[F, A, *]]
    with StackSafeMonad[Service[F, A, *]]
    with Alternative[Service[F, A, *]] {
  override def map[B, C](fb: Service[F, A, B])(f: B => C): Service[F, A, C] =
    fb.map(f)

  def pure[B](b: B): Service[F, A, B] =
    Service.pure(b)

  def flatMap[B, C](fb: Service[F, A, B])(f: B => Service[F, A, C]): Service[F, A, C] =
    fb.flatMap(f)

  def combineK[B](x: Service[F, A, B], y: Service[F, A, B]): Service[F, A, B] =
    x.orElse(y)

  def empty[B]: Service[F, A, B] =
    Service.pass
}

private[http4s] trait ServiceInstances {
  implicit def catsInstancesForHttp4sService[F[_], A]
      : Monad[Service[F, A, *]] with Alternative[Service[F, A, *]] =
    new ServiceInstance[F, A] {}

  implicit def catsMtlLocalForHttp4sService[F[_], A]: Local[Service[F, A, *], A] =
    new Local[Service[F, A, *], A] {
      val applicative = Applicative[Service[F, A, *]]
      def ask[E >: A]: Service[F, A, E] =
        Service.ask.asInstanceOf[Service[F, A, E]]
      def local[B](fb: Service[F, A, B])(f: A => A): Service[F, A, B] =
        fb.local(f)
    }
}
