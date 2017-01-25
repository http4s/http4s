/*
package org.http4s
package util

trait EitherSyntax {
  implicit class EitherOps[A, B](self: Either[A, B]) {
    def flatMap[C](f: B => Either[A, C]): Either[A, C] =
      self match {
        case left @ Left(a) => left.asInstanceOf[Either[A, C]]
        case Right(b) => f(b)
      }

    def map[C](f: B => C): Either[A, C] =
      self.right.map(f)

    def bimap[C, D](fa: A => C, fb: B => D): Either[C, D] =
      self.fold(a => Left(fa(a)), fb => Right(fb(b)))

    def getOrElse[B2 >: B](default: B2): B2 =
      self.fold(default, identity)
  }
}

object either extends EitherSyntax
 */
