package org.http4s.metrics

import org.http4s.util.{SizedSeq, SizedSeq0}

trait CustomLabels[+SL <: SizedSeq[String]] {
  def labels: SL
  def values: SL
}

object CustomLabels {
  def apply[SL <: SizedSeq[String]](pLabels: SL, pValues: SL): CustomLabels[SL] =
    new CustomLabels[SL] {
      override def labels: SL = pLabels
      override def values: SL = pValues
    }
}

case class EmptyCustomLabels() extends CustomLabels[SizedSeq0[String]] {
  override def labels: SizedSeq0[String] = SizedSeq0[String]()
  override def values: SizedSeq0[String] = SizedSeq0[String]()
}
