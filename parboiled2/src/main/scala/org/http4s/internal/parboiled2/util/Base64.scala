/**
  * A very fast and memory efficient class to encode and decode to and from BASE64 in full accordance
  * with RFC 2045.<br><br>
  * On Windows XP sp1 with 1.4.2_04 and later ;), this encoder and decoder is about 10 times faster
  * on small arrays (10 - 1000 bytes) and 2-3 times as fast on larger arrays (10000 - 1000000 bytes)
  * compared to <code>sun.misc.Encoder()/Decoder()</code>.<br><br>
  *
  * On byte arrays the encoder is about 20% faster than Jakarta Commons Base64 Codec for encode and
  * about 50% faster for decoding large arrays. This implementation is about twice as fast on very small
  * arrays (&lt 30 bytes). If source/destination is a <code>String</code> this
  * version is about three times as fast due to the fact that the Commons Codec result has to be recoded
  * to a <code>String</code> from <code>byte[]</code>, which is very expensive.<br><br>
  *
  * This encode/decode algorithm doesn't create any temporary arrays as many other codecs do, it only
  * allocates the resulting array. This produces less garbage and it is possible to handle arrays twice
  * as large as algorithms that create a temporary array. (E.g. Jakarta Commons Codec). It is unknown
  * whether Sun's <code>sun.misc.Encoder()/Decoder()</code> produce temporary arrays but since performance
  * is quite low it probably does.<br><br>
  *
  * The encoder produces the same output as the Sun one except that the Sun's encoder appends
  * a trailing line separator if the last character isn't a pad. Unclear why but it only adds to the
  * length and is probably a side effect. Both are in conformance with RFC 2045 though.<br>
  * Commons codec seem to always att a trailing line separator.<br><br>
  *
  * <b>Note!</b>
  * The encode/decode method pairs (types) come in three versions with the <b>exact</b> same algorithm and
  * thus a lot of code redundancy. This is to not create any temporary arrays for transcoding to/from different
  * format types. The methods not used can simply be commented out.<br><br>
  *
  * There is also a "fast" version of all decode methods that works the same way as the normal ones, but
  * har a few demands on the decoded input. Normally though, these fast verions should be used if the source if
  * the input is known and it hasn't bee tampered with.<br><br>
  *
  * If you find the code useful or you find a bug, please send me a note at base64 @ miginfocom . com.
  *
  * Licence (BSD):
  * ==============
  *
  * Copyright (c) 2004, Mikael Grev, MiG InfoCom AB. (base64 @ miginfocom . com)
  * All rights reserved.
  *
  * Redistribution and use in source and binary forms, with or without modification,
  * are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright notice, this list
  * of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice, this
  * list of conditions and the following disclaimer in the documentation and/or other
  * materials provided with the distribution.
  * Neither the name of the MiG InfoCom AB nor the names of its contributors may be
  * used to endorse or promote products derived from this software without specific
  * prior written permission.
  *
  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
  * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
  * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
  * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
  * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
  * OF SUCH DAMAGE.
  *
  * @version 2.2
  * @author Mikael Grev
  *         Date: 2004-aug-02
  *         Time: 11:31:11
  *
  * Adapted in 2009 by Mathias Doenitz.
  */

package org.http4s.internal.parboiled2.util

private[http4s] object Base64 {
  private var RFC2045: Base64 = _
  private var CUSTOM: Base64 = _

  def custom(): Base64 = {
    if (CUSTOM == null) {
      CUSTOM = new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-_")
    }
    CUSTOM
  }

  def rfc2045(): Base64 = {
    if (RFC2045 == null) {
      RFC2045 = new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=")
    }
    RFC2045
  }
}

private[http4s] class Base64(alphabet: String) {
  if (alphabet == null || alphabet.length() != 65) {
    throw new IllegalArgumentException()
  }

  val CA = alphabet.substring(0, 64).toCharArray
  val fillChar = alphabet.charAt(64)
  val IA: Array[Int] = Array.fill(256)(-1)

  (0 until CA.length).foreach { i =>
    IA(CA(i).toInt) = i
  }

  IA(fillChar.toInt) = 0

  def getAlphabet: Array[Char] = {
    CA
  }

  /**
    * Decodes a BASE64 encoded char array. All illegal characters will be ignored and can handle both arrays with
    * and without line separators.
    *
    * @param sArr The source array. <code>null</code> or length 0 will return an empty array.
    * @return The decoded array of bytes. May be of length 0. Will be <code>null</code> if the legal characters
    *         (including '=') isn't divideable by 4.  (I.e. definitely corrupted).
    */
  def decode(sArr: Array[Char]): Array[Byte] = {
    // Check special case
    val sLen = if(sArr != null) {
      sArr.length
    }
    else {
      0
    }

    if (sLen == 0) {
      return Array.empty[Byte]
    }

    // Count illegal characters (including '\r', '\n') to know what size the returned array will be,
    // so we don't have to reallocate & copy it later.
    // If input is "pure" (I.e. no line separators or illegal chars) base64 this loop can be commented out.
    var sepCnt = 0 // Number of separator characters. (Actually illegal characters, but that's a bonus...)
    (0 until sLen).foreach { i =>
      if (IA(sArr(i).toInt) < 0) {
        sepCnt += 1
      }
    }

    // Check so that legal chars (including '=') are evenly divideable by 4 as specified in RFC 2045.
    if ((sLen - sepCnt) % 4 != 0) {
      return null
    }

    var pad = 0
    var i = sLen-1
    while (i > 0 && IA(sArr(i).toInt) <= 0) {
      if (sArr(i) == fillChar) {
        pad += 1
      }
      i -= 1
    }

    val len = ((sLen - sepCnt) * 6 >> 3) - pad

    val dArr = Array.ofDim[Byte](len) // Preallocate byte[] of exact length
    var s = 0
    var d = 0
    while (d < len) {
      // Assemble three bytes into an int from four "valid" characters.
      var i = 0
      var j = 0

      // j only increased if a valid char was found.
      while (j < 4) {
        val c = IA(sArr(s).toInt)
        s += 1
        if (c >= 0) {
          i |= c << (18 - j * 6)
        } else {
          j -= 1
        }

        j += 1
      }

      // Add the bytes
      dArr(d) = (i >> 16).toByte
      d += 1
      if (d < len) {
        dArr(d) = (i >> 8).toByte
        d += 1
        if (d < len) {
          dArr(d) = i.toByte
          d += 1
        }
      }
    }

    dArr
  }


  /**
    * Decodes a BASE64 encoded char array that is known to be resonably well formatted. The method is about twice as
    * fast as {@link #decode(char[])}. The preconditions are:<br>
    * + The array must have a line length of 76 chars OR no line separators at all (one line).<br>
    * + Line separator must be "\r\n", as specified in RFC 2045
    * + The array must not contain illegal characters within the encoded string<br>
    * + The array CAN have illegal characters at the beginning and end, those will be dealt with appropriately.<br>
    *
    * @param sArr The source array. Length 0 will return an empty array. <code>null</code> will throw an exception.
    * @return The decoded array of bytes. May be of length 0.
    */
  def decodeFast(sArr: Array[Char]): Array[Byte] = {
    // Check special case
    val sLen = sArr.length
    if (sLen == 0) {
      return Array.empty[Byte]
    }

    // Start and end index after trimming.
    var sIx = 0
    var eIx = sLen - 1

    // Trim illegal chars from start
    while (sIx < eIx && IA(sArr(sIx).toInt) < 0) {
      sIx += 1
    }

    // Trim illegal chars from end
    while (eIx > 0 && IA(sArr(eIx).toInt) < 0) {
      eIx -= 1
    }

    // get the padding count (=) (0, 1 or 2)
    // Count '=' at end.
    val pad = if (sArr(eIx) == fillChar) {
      if (sArr(eIx - 1) == fillChar) {
        2
      }
      else {
        1
      }
    }
    else {
      0
    }

    // Content count including possible separators
    val cCnt = eIx - sIx + 1

    // Count '=' at end.
    val sepCnt = if (sLen > 76) {
        (if (sArr(76) == '\r') {
          cCnt / 78
        }
        else {
          0
        }) << 1
      }
      else {
        0
      }

    val len = ((cCnt - sepCnt) * 6 >> 3) - pad // The number of decoded bytes
    val dArr = Array.ofDim[Byte](len);       // Preallocate byte() of exact length

    // Decode all but the last 0 - 2 bytes.
    var d = 0
    var cc = 0
    val eLen = (len / 3) * 3

    while (d < eLen) {
      // Assemble three bytes into an int from four "valid" characters.
      var i = IA(sArr(sIx).toInt) << 18
      sIx += 1
      i = i | IA(sArr(sIx).toInt) << 12
      sIx += 1
      i = i | IA(sArr(sIx).toInt) << 6
      sIx += 1
      i = i | IA(sArr(sIx).toInt)
      sIx += 1

      // Add the bytes
      dArr(d) = (i >> 16).toByte
      d += 1
      dArr(d) = (i >> 8).toByte
      d += 1
      dArr(d) = i.toByte
      d += 1

      // If line separator, jump over it.
      cc += 1
      if (sepCnt > 0 && cc == 19) {
        sIx += 2
        cc = 0
      }
    }

    if (d < len) {
      // Decode last 1-3 bytes (incl '=') into 1-3 bytes
      var i = 0
      var j = 0
      while (sIx <= eIx - pad) {
        i |= IA(sArr(sIx).toInt) << (18 - j * 6)
        sIx += 1
        j += 1
      }

      var r = 16
      while (d < len) {
        dArr(d) = (i >> r).toByte
        d += 1
        r -= 8
      }
    }

    dArr
  }

  /**
    * Encodes a raw byte array into a BASE64 <code>String</code> representation in accordance with RFC 2045.
    *
    * @param sArr    The bytes to convert. If <code>null</code> or length 0 an empty array will be returned.
    * @param lineSep Optional "\r\n" after 76 characters, unless end of file.<br>
    *                No line separator will be in breach of RFC 2045 which specifies max 76 per line but will be a
    *                little faster.
    * @return A BASE64 encoded array. Never <code>null</code>.
    */
  def encodeToString(sArr: Array[Byte], lineSep: Boolean): String = {
    // Reuse char[] since we can't create a String incrementally anyway and StringBuffer/Builder would be slower.
    new String(encodeToChar(sArr, lineSep))
  }

  /**
    * Encodes a raw byte array into a BASE64 <code>char[]</code> representation i accordance with RFC 2045.
    *
    * @param sArr    The bytes to convert. If <code>null</code> or length 0 an empty array will be returned.
    * @param lineSep Optional "\r\n" after 76 characters, unless end of file.<br>
    *                No line separator will be in breach of RFC 2045 which specifies max 76 per line but will be a
    *                little faster.
    * @return A BASE64 encoded array. Never <code>null</code>.
    */
  def encodeToChar(sArr: Array[Byte], lineSep: Boolean): Array[Char] = {
    // Check special case
    val sLen = if (sArr != null) {
      sArr.length
    }
    else {
      0
    }

    if (sLen == 0) {
      return Array.empty[Char]
    }

    val eLen = (sLen / 3) * 3              // Length of even 24-bits.
    val cCnt = ((sLen - 1) / 3 + 1) << 2   // Returned character count

    // Length of returned array
    val dLen = cCnt + (if (lineSep == true) {
      (cCnt - 1) / 76 << 1
    }
    else {
      0
    })


    val dArr = Array.ofDim[Char](dLen)

    // Encode even 24-bits
    var s = 0
    var d = 0
    var cc = 0
    while (s < eLen) {
      // Copy next three bytes into lower 24 bits of int, paying attension to sign.
      var i = (sArr(s) & 0xff) << 16
      s += 1
      i = i | ((sArr(s) & 0xff) << 8)
      s += 1
      i = i | (sArr(s) & 0xff)
      s += 1
      
      // Encode the int into four chars
      dArr(d) = CA((i >>> 18) & 0x3f)
      d += 1
      dArr(d) = CA((i >>> 12) & 0x3f)
      d += 1
      dArr(d) = CA((i >>> 6) & 0x3f)
      d += 1
      dArr(d) = CA(i & 0x3f)
      d += 1

      // Add optional line separator
      cc += 1
      if (lineSep && cc == 19 && d < dLen - 2) {
        dArr(d) = '\r'
        d += 1
        dArr(d) = '\n'
        d += 1
        cc = 0
      }
    }

    // Pad and encode last bits if source isn't even 24 bits.
    val left = sLen - eLen; // 0 - 2.
    if (left > 0) {
      // Prepare the int
      val i = ((sArr(eLen) & 0xff) << 10) | (if (left == 2) {
        (sArr(sLen - 1) & 0xff) << 2
      }
      else {
        0
      })

      // Set last four chars
      dArr(dLen - 4) = CA(i >> 12)
      dArr(dLen - 3) = CA((i >>> 6) & 0x3f)
      dArr(dLen - 2) = if(left == 2) {
        CA(i & 0x3f)
      }
      else {
        fillChar
      }
      dArr(dLen - 1) = fillChar
    }
    
    dArr
  }
}
