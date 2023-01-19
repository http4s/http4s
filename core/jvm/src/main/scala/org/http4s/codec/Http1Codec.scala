package org.http4s.codec

import cats.free.FreeInvariantMonoidal

object Http1Codec {
  sealed trait Op[A]
  final case class StringLiteral(s: String) extends Op[Unit]
  final case class CharLiteral(c: Char) extends Op[Unit]
  case object Digit extends Op[Char]
  final case class ListOf[A](codec: Http1Codec[A]) extends Op[List[A]]

  def stringLiteral(s: String): Http1Codec[Unit] =
    lift(StringLiteral(s))
  def charLiteral(c: Char): Http1Codec[Unit] =
    lift(CharLiteral(c))
  def digit: Http1Codec[Char] =
    lift(Digit)
  def listOf[A](codec: Http1Codec[A]): Http1Codec[List[A]] =
    lift(ListOf(codec))

  def lift[A](op: Op[A]): Http1Codec[A] =
    FreeInvariantMonoidal.lift(op)
}
