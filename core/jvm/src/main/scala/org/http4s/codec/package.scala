package org.http4s

import cats.free.FreeInvariantMonoidal

package object codec {
  type Http1Codec[A] = FreeInvariantMonoidal[Http1Codec.Op, A]
}
