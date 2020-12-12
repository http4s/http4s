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

package com.example.http4s.blaze.demo.server.endpoints.auth

import cats.effect.Sync
import cats.syntax.apply._
import org.http4s.BasicCredentials

trait AuthRepository[F[_], A] {
  def persist(entity: A): F[Unit]
  def find(entity: A): F[Option[A]]
}

object AuthRepository {
  implicit def authUserRepo[F[_]](implicit F: Sync[F]): AuthRepository[F, BasicCredentials] =
    new AuthRepository[F, BasicCredentials] {
      private val storage = scala.collection.mutable.Set[BasicCredentials](
        BasicCredentials("gvolpe", "123456")
      )
      override def persist(entity: BasicCredentials): F[Unit] =
        F.delay(storage.add(entity)) *> F.unit
      override def find(entity: BasicCredentials): F[Option[BasicCredentials]] =
        F.delay(storage.find(_ == entity))
    }
}
