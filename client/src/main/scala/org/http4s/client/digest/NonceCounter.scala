/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.digest

import java.util.Base64

import cats.effect.{Concurrent, Timer}

import scala.concurrent.duration._
import scala.util.Random

trait NonceCounter[F[_]] {

  /** Returns `cnonce` and next `nc` value for the given nonce */
  def next(nonce: String): F[(String, Long)]

  /** Call when a conversation is over, to prevent unbounded growth. */
  def evict(nonce: String): F[Unit]

  /** Call when a conversation is over, to prevent unbounded growth. */
  def evictAll: F[Unit]
}

object NonceCounter {
  val MaxNonce: Long = java.lang.Long.parseLong("FFFFFFFF", 16)
  def nextCnonce(): String = {
    val bytes = new Array[Byte](16)
    Random.nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }
  def defaultNonceCounter[F[_]: Concurrent: Timer]: NonceCounter[F] =
    new SimpleInMemoryStoreNonceCounter[F](1.minute, nextCnonce, MaxNonce)
}
