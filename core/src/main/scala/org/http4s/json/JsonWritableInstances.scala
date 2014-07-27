package org.http4s
package json

trait JsonWritableInstances[-J] {
  implicit def jsonWritable: Writable[J]
}
