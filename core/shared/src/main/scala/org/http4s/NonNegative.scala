package org.http4s

import java.io.Serializable
import scala.math.BigInt

sealed abstract class NonNegative extends Serializable {
  import NonNegative._

  def toInt: Option[Int]
  def toLong: Option[Long]
  def toBigInt: BigInt

  override def hashCode = toBigInt.hashCode

  override def equals(that: Any) = (this, that) match {
    case (LongNonNegative(x), LongNonNegative(y)) => x == y
    case (BigIntNonNegative(x), BigIntNonNegative(y)) => x == y
    case (x, y: NonNegative) => x.toBigInt == y.toBigInt
    case (_, _) => false
  }
}

object NonNegative {
  val zero: NonNegative =
    fromLong(0L).get

  def fromLong(value: Long): Option[NonNegative] =
    if ((value >= 0) && (value <= Long.MaxValue)) Some(LongNonNegative(value))
    else None

  private case class LongNonNegative(value: Long) extends NonNegative {
    def toInt: Option[Int] =
      if (value < Int.MaxValue) Some(value.toInt) else None
    def toLong: Option[Long] =
      Some(value)
    def toBigInt: BigInt =
      BigInt(value)
    override def toString =
      value.toString
  }

  def fromBigInt(value: BigInt): Option[NonNegative] =
    if (value >= BigInt(0)) Some(BigIntNonNegative(value))
    else None

  private case class BigIntNonNegative(value: BigInt) extends NonNegative {
    def toInt: Option[Int] =
      if (value.isValidInt) Some(value.toInt) else None
    def toLong: Option[Long] =
      if (value.isValidLong) Some(value.toLong) else None
    def toBigInt: BigInt =
      value
    override def toString: String =
      value.toString
  }
}
