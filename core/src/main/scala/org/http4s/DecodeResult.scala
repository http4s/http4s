package org.http4s

import scalaz.{\/, EitherT}
import scalaz.concurrent.Task

object DecodeResult {
  def apply[A](task: Task[DecodeFailure \/ A]): DecodeResult[A] =
    EitherT(task)

  def success[A](a: Task[A]): DecodeResult[A] =
    EitherT.right(a)

  def success[A](a: A): DecodeResult[A] =
    success(Task.now(a))

  def failure[A](e: Task[DecodeFailure]): DecodeResult[A] =
    EitherT.left(e)

  def failure[A](e: DecodeFailure): DecodeResult[A] =
    failure(Task.now(e))
}
