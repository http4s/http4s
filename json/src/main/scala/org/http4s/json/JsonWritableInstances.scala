package org.http4s.json

import org.http4s.Writable

trait JsonWritableInstances[-J] {
  implicit def jsonWritable: Writable[J]
}
