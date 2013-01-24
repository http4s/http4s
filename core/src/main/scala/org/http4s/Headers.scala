package org.http4s

trait Headers extends Seq[Header]

trait Header {
  def name: String
  def value: String
}

trait RequestHeaders extends Headers

object RequestHeaders {
  val Empty: RequestHeaders = new RequestHeaders {
    def length = 0
    def apply(idx: Int) = throw new NoSuchElementException
    def iterator = Iterator.empty
  }
}
