package org.http4s.internal 

object BytesToHex {
  private val hexArray: Array[Char] = "0123456789ABCDEF".toCharArray();
  def bytesToHex(bytes: Array[Byte]): String = {
    val hexChars = new Array[Char](bytes.length * 2)
    for (j <- 0 until bytes.length) {
      val v: Int = bytes(j) & 0xFF
      hexChars(j * 2) = hexArray(v >>> 4)
      hexChars(j * 2 + 1) = hexArray(v & 0x0F)
    }
    new String(hexChars)
  }
}
