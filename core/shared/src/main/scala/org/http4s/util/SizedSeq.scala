package org.http4s.util

sealed trait SizedSeq[+A] {
  def toSeq: Seq[A]
}

abstract class SizedSeqBase[+A] extends SizedSeq[A] {
  protected val seq: Seq[A]
  override def toSeq: Seq[A] = seq
}

// format: off
case class SizedSeq0[+A]() extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq.empty
}

case class SizedSeq1[+A](s1: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1)
}

case class SizedSeq2[+A](s1: A, s2: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2)
}

case class SizedSeq3[+A](s1: A, s2: A, s3: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3)
}

case class SizedSeq4[+A](s1: A, s2: A, s3: A, s4: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4)
}

case class SizedSeq5[+A](s1: A, s2: A, s3: A, s4: A, s5: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4, s5)
}

case class SizedSeq6[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6)
}

case class SizedSeq7[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7)
}

case class SizedSeq8[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8)
}

case class SizedSeq9[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9)
}

case class SizedSeq10[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A) extends SizedSeqBase[A] {
  override val seq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10)
}
// format: on