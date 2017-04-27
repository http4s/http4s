package org.http4s

import cats.data._
import cats.syntax.either._
import fs2.Task

object DecodeResult {
  def apply[A](fa: Task[Either[DecodeFailure, A]]): DecodeResult[A] =
    EitherT(fa)

  def success[A](a: Task[A]): DecodeResult[A] =
    DecodeResult(a.map(Either.right(_)))

  def success[A](a: A): DecodeResult[A] =
    success(Task.now(a))

  def failure[A](e: Task[DecodeFailure]): DecodeResult[A] =
    DecodeResult(e.map(Either.left(_)))

  def failure[A](e: DecodeFailure): DecodeResult[A] =
    failure(Task.now(e))
}
