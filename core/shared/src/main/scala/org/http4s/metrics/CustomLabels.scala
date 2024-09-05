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

package org.http4s.metrics

import org.http4s.util.SizedSeq
import org.http4s.util.SizedSeq0

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

abstract sealed case class EmptyCustomLabels private () extends CustomLabels[SizedSeq0[String]] {
  override def labels: SizedSeq0[String] = SizedSeq0[String]()
  override def values: SizedSeq0[String] = SizedSeq0[String]()
}

object EmptyCustomLabels {
  private[this] val instance: EmptyCustomLabels = new EmptyCustomLabels() {}
  def apply[A](): EmptyCustomLabels = instance
}
