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

// format: off
final case class SizedSeq0[+A] private () extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq.empty[A]
}
object SizedSeq0 {
  private[this] val instance = SizedSeq0[Nothing]()
  def apply[A](): SizedSeq0[A] = instance
}

final case class SizedSeq1[+A](s1: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1)
}

final case class SizedSeq2[+A](s1: A, s2: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2)
}

final case class SizedSeq3[+A](s1: A, s2: A, s3: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3)
}

final case class SizedSeq4[+A](s1: A, s2: A, s3: A, s4: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4)
}

final case class SizedSeq5[+A](s1: A, s2: A, s3: A, s4: A, s5: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5)
}

final case class SizedSeq6[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6)
}

final case class SizedSeq7[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7)
}

final case class SizedSeq8[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8)
}

final case class SizedSeq9[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9)
}

final case class SizedSeq10[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10)
}

final case class SizedSeq11[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11)
}

final case class SizedSeq12[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12)
}

final case class SizedSeq13[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13)
}

final case class SizedSeq14[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14)
}

final case class SizedSeq15[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15)
}

final case class SizedSeq16[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16)
}

final case class SizedSeq17[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A, s17: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17)
}

final case class SizedSeq18[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A, s17: A, s18: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18)
}

final case class SizedSeq19[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A, s17: A, s18: A, s19: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19)
}

final case class SizedSeq20[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A, s17: A, s18: A, s19: A, s20: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19, s20)
}

final case class SizedSeq21[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A, s17: A, s18: A, s19: A, s20: A, s21: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19, s20, s21)
}

final case class SizedSeq22[+A](s1: A, s2: A, s3: A, s4: A, s5: A, s6: A, s7: A, s8: A, s9: A, s10: A, s11: A, s12: A, s13: A, s14: A, s15: A, s16: A, s17: A, s18: A, s19: A, s20: A, s21: A, s22: A) extends SizedSeq[A] {
  val toSeq: Seq[A] = Seq(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19, s20, s21, s22)
}
// format: on
