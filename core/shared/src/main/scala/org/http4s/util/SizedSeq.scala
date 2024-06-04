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
