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

// Copied verbatim from https://github.com/scala-js/scala-js-dom
// The MIT License (MIT)
// Copyright (c) 2013 Li Haoyi
// Documentation marked "MDN" is thanks to Mozilla Contributors
// at https://developer.mozilla.org/en-US/docs/Web/API and available
// under the Creative Commons Attribution-ShareAlike v2.5 or later.
// http://creativecommons.org/licenses/by-sa/2.5/

package org.http4s.js.crypto

import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.typedarray.ArrayBufferView

@js.native
@JSGlobalScope
object GlobalCrypto extends js.Object {
  val crypto: Crypto = js.native
}

/** The Crypto interface represents basic cryptography features available in the current context. It
  * allows access to a cryptographically strong random number generator and to cryptographic
  * primitives.
  *
  * MDN
  */
@js.native
trait Crypto extends js.Object {

  /** Returns a SubtleCrypto object providing access to common cryptographic primitives, like
    * hashing, signing, encryption or decryption.
    *
    * MDN
    */
  val subtle: SubtleCrypto = js.native

  /** Fills the passed TypedArray with cryptographically sound random values.
    *
    * MDN
    */
  def getRandomValues(array: ArrayBufferView): ArrayBufferView = js.native
}

@js.native
trait Algorithm extends js.Object {
  var name: String = js.native
}

/** The KeyAlgorithm dictionary represents information about the contents of a given CryptoKey
  * object.
  *
  * See [[http://www.w3.org/TR/WebCryptoAPI/#key-algorithm-dictionary ¶12 KeyAlgorithm dictionary]]
  * in w3c spec.
  */
@js.native
trait KeyAlgorithm extends Algorithm

/** A HashAlgorithm type is not defined in the
  * [[http://www.w3.org/TR/WebCryptoAPI/ W3C Web Crypto API]], even though a
  * [[http://www.w3.org/TR/WebCryptoAPI/#key-algorithm-dictionary KeyAlgorithm dictionary]] type is.
  * There are nevertheless a number of indications that HashAlgorithm's are a type of their own, as
  * searching the spec will show.
  */
@js.native
trait HashAlgorithm extends Algorithm

object HashAlgorithm {

  private def named(name: String): HashAlgorithm =
    js.Dynamic.literal(name = name).asInstanceOf[HashAlgorithm]

  val `SHA-1` = named("SHA-1")
  val `SHA-256` = named("SHA-256")
  val `SHA-384` = named("SHA-384")
  val `SHA-512` = named("SHA-512")
}

/** The CryptoKey object represents an opaque reference to keying material that is managed by the
  * user agent.
  *
  * defined at
  * [[http://www.w3.org/TR/WebCryptoAPI/#cryptokey-interface ¶13 The CryptoKey Interface]]
  */
@js.native
trait CryptoKey extends js.Object {
  val `type`: String = js.native

  val extractable: Boolean = js.native

  val algorithm: KeyAlgorithm = js.native

  val usages: js.Array[KeyUsage] = js.native
}

/** The CryptoKeyPair dictionary represents an asymmetric key pair that is comprised of both public
  * and private keys. defined at
  * [[http://www.w3.org/TR/WebCryptoAPI/#keypair ¶17 CryptoKeyPair dictionary]] of spec
  */
@js.native
trait CryptoKeyPair extends js.Object {
  val publicKey: CryptoKey = js.native
  val privateKey: CryptoKey = js.native
}

@js.native
trait RsaOtherPrimesInfo extends js.Object {
  var r: String = js.native

  var d: String = js.native

  var t: String = js.native
}

@js.native
trait JsonWebKey extends js.Object {
  var kty: String = js.native

  var use: String = js.native

  var key_ops: js.Array[String] = js.native

  var alg: js.Array[String] = js.native

  var ext: Boolean = js.native

  var crv: String = js.native

  var x: String = js.native

  var y: String = js.native

  var d: String = js.native

  var n: String = js.native

  var e: String = js.native

  var p: String = js.native

  var q: String = js.native

  var dp: String = js.native

  var dq: String = js.native

  var qi: String = js.native

  var oth: js.Array[String] = js.native

  var k: String = js.native
}

/** [[http://www.w3.org/TR/WebCryptoAPI/#subtlecrypto-interface w3c ¶14 Subtle Crytpo interface]]
  *
  * The SubtleCrypto interface represents a set of cryptographic primitives. It is available via the
  * Crypto.subtle properties available in a window context (via Window.crypto).
  *
  * MDN
  */
@js.native
trait SubtleCrypto extends js.Object {

  /** Returns a Promise of the encrypted data corresponding to the clear text, algorithm and key
    * given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-encrypt ¶14.3.1 The encrypt method]]
    */
  def encrypt(
      algorithm: AlgorithmIdentifier,
      key: CryptoKey,
      data: BufferSource): js.Promise[js.Any] = js.native

  /** Returns a Promise of the clear data corresponding to the encrypted text, algorithm and key
    * given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-decrypt ¶14.3.2 The decrypt method]]
    */
  def decrypt(
      algorithm: AlgorithmIdentifier,
      key: CryptoKey,
      data: BufferSource): js.Promise[js.Any] = js.native

  /** Returns a Promise of the signature corresponding to the text, algorithm and key given as
    * parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-sign ¶14.3.3 The sign method]]
    */
  def sign(algorithm: AlgorithmIdentifier, key: CryptoKey, data: BufferSource): js.Promise[js.Any] =
    js.native

  /** Returns a Promise of a Boolean value indicating if the signature given as parameter matches
    * the text, algorithm and key also given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-verify ¶14.3.4 The verify method]]
    */
  def verify(
      algorithm: AlgorithmIdentifier,
      key: CryptoKey,
      signature: BufferSource,
      data: BufferSource): js.Promise[js.Any] = js.native

  /** Returns a Promise of a digest generated from the algorithm and text given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-digest ¶14.3.5 The digest method]] We
    * are a bit more precise than the official definition by requiring a HashAlgorithmIdentifier
    * rather than an AlgorithmIdentifier for the algorithm parameter.
    */
  def digest(algorithm: HashAlgorithmIdentifier, data: BufferSource): js.Promise[js.Any] = js.native

  /** Returns a Promise of a newly generated CryptoKey, for symmetrical algorithms, or a
    * CryptoKeyPair, containing two newly generated keys, for asymmetrical algorithm, that matches
    * the algorithm, the usages and the extractability given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-generateKey ¶14.3.6 The generateKey method]]
    *
    * We are being a bit more precise than the official definition by requiring a
    * KeyAlgorithmIdentifier rather than an AlgorithmIdentifier for the algorithm field.
    */
  def generateKey(
      algorithm: KeyAlgorithmIdentifier,
      extractable: Boolean,
      keyUsages: js.Array[KeyUsage]): js.Promise[js.Any] = js.native

  /** Returns a Promise of a newly generated CryptoKey derivated from a master key and a specific
    * algorithm given as parameters. MDF
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-deriveKey ¶14.3.7 The deriveKey method]]
    *
    * We are being a bit more precise than the official definition by requiring
    * KeyAlgorithmIdentifier for derivedKeyType
    */
  def deriveKey(
      algorithm: AlgorithmIdentifier,
      baseKey: CryptoKey,
      derivedKeyType: KeyAlgorithmIdentifier,
      extractable: Boolean,
      keyUsages: js.Array[KeyUsage]): js.Promise[js.Any] = js.native

  /** Returns a Promise of a newly generated buffer of pseudo-random bits derivated from a master
    * key and a specific algorithm given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-deriveBits ¶14.3.8 The deriveBits method]]
    */
  def deriveBits(
      algorithm: AlgorithmIdentifier,
      baseKey: CryptoKey,
      length: Double): js.Promise[js.Any] = js.native

  /** Returns a Promise of a CryptoKey corresponding to the format, the algorithm, the raw key data,
    * the usages and the extractability given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-importKey ¶14.3.9 The importKey method]]
    *
    * We are being a bit more precise than the official definition by requiring a
    * KeyAlgorithmIdentifier rather than an AlgorithmIdentifier for the algorithm field.
    */
  def importKey(
      format: KeyFormat,
      keyData: BufferSource,
      algorithm: KeyAlgorithmIdentifier,
      extractable: Boolean,
      keyUsages: js.Array[KeyUsage]): js.Promise[js.Any] = js.native

  /** Returns a Promise of a CryptoKey corresponding to the format, the algorithm, the raw key data,
    * the usages and the extractability given as parameters. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-importKey ¶14.3.9 The importKey method]]
    *
    * We are being a bit more precise than the official definition by requiring a
    * KeyAlgorithmIdentifier rather than an AlgorithmIdentifier for the algorithm field.
    */
  def importKey(
      format: KeyFormat,
      keyData: JsonWebKey,
      algorithm: KeyAlgorithmIdentifier,
      extractable: Boolean,
      keyUsages: js.Array[KeyUsage]): js.Promise[js.Any] = js.native

  /** Returns a Promise of a buffer containing the key in the format requested.
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-exportKey ¶14.3.10 The exportKey method]]
    */
  def exportKey(format: KeyFormat, key: CryptoKey): js.Promise[js.Any] = js.native

  /** Returns a Promise of a wrapped symmetric key for usage (transfer, storage) in unsecure
    * environments. The wrapped buffer returned is in the format given in parameters, and contained
    * the key wrapped by the give wrapping key with the given algorithm.
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-wrapKey ¶14.3.11 The wrapKey method]]
    */
  def wrapKey(
      format: KeyFormat,
      key: CryptoKey,
      wrappingKey: CryptoKey,
      wrapAlgorithm: AlgorithmIdentifier): js.Promise[js.Any] = js.native

  /** Returns a Promise of a CryptoKey corresponding to the wrapped key given in parameter. MDN
    *
    * Defined at
    * [[http://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-unwrapKey ¶14.3.12 The unwrapKey method]]
    *
    * We are being a bit more precise than the official definition by requiring a
    * KeyAlgorithmIdentifier rather than an AlgorithmIdentifier.
    */
  def unwrapKey(
      format: String,
      wrappedKey: BufferSource,
      unwrappingKey: CryptoKey,
      unwrapAlgorithm: AlgorithmIdentifier,
      unwrappedKeyAlgorithm: AlgorithmIdentifier,
      extractable: Boolean,
      keyUsages: js.Array[KeyUsage]): js.Promise[js.Any] = js.native
}

// RSASSA-PKCS1-v1_5

@js.native
trait RsaKeyGenParams extends KeyAlgorithm {
  var modulusLength: Double = js.native

  var publicExponent: BigInteger = js.native
}

object RsaKeyGenParams {
  @inline
  def apply(name: String, modulusLength: Long, publicExponent: BigInteger): RsaKeyGenParams =
    js.Dynamic
      .literal(name = name, modulusLength = modulusLength.toDouble, publicExponent = publicExponent)
      .asInstanceOf[RsaKeyGenParams]
}

@js.native
trait RsaHashedKeyGenParams extends RsaKeyGenParams {
  var hash: HashAlgorithmIdentifier = js.native
}

object RsaHashedKeyGenParams {
  @inline
  def apply(
      name: String,
      modulusLength: Long,
      publicExponent: BigInteger,
      hash: HashAlgorithmIdentifier): RsaHashedKeyGenParams =
    js.Dynamic
      .literal(
        name = name,
        modulusLength = modulusLength.toDouble,
        publicExponent = publicExponent,
        hash = hash.asInstanceOf[js.Any])
      .asInstanceOf[RsaHashedKeyGenParams]
}

@js.native
trait RsaKeyAlgorithm extends KeyAlgorithm {
  var modulusLength: Double = js.native

  var publicExponent: BigInteger = js.native
}

object RsaKeyAlgorithm {
  @inline
  def apply(name: String, modulusLength: Long, publicExponent: BigInteger): RsaKeyAlgorithm =
    js.Dynamic
      .literal(name = name, modulusLength = modulusLength.toDouble, publicExponent = publicExponent)
      .asInstanceOf[RsaKeyAlgorithm]
}

/** see W3C doc
  * [[http://www.w3.org/TR/WebCryptoAPI/#RsaHashedKeyAlgorithm-dictionary 20.6. RsaHashedKeyAlgorithm dictionary]]
  */
@js.native
trait RsaHashedKeyAlgorithm extends RsaKeyAlgorithm {

  /** Note that section
    * [[http://www.w3.org/TR/WebCryptoAPI/#RsaHashedKeyAlgorithm-dictionary 20.6. RsaHashedKeyAlgorithm dictionary]]
    * of the W3C documentation uses a KeyAlgorithm here, and not what seems more correct a
    * HashAlgorithmIdentifier.
    */
  var hash: HashAlgorithmIdentifier = js.native
}

object RsaHashedKeyAlgorithm {
  @inline
  def apply(
      name: String,
      modulusLength: Long,
      publicExponent: BigInteger,
      hash: HashAlgorithmIdentifier): RsaHashedKeyAlgorithm =
    js.Dynamic
      .literal(
        name = name,
        modulusLength = modulusLength.toDouble,
        publicExponent = publicExponent,
        hash = hash.asInstanceOf[js.Any])
      .asInstanceOf[RsaHashedKeyAlgorithm]

  /** see [[http://www.w3.org/TR/WebCryptoAPI/#rsassa-pkcs1 ¶20. RSASSA-PKCS1-v1_5]] of w3c spec
    */
  def `RSASSA-PKCS1-v1_5`(
      modulusLength: Long,
      publicExponent: BigInteger,
      hash: HashAlgorithmIdentifier): RsaHashedKeyAlgorithm =
    apply("RSASSA-PKCS1-v1_5", modulusLength, publicExponent, hash)

  /** see [[http://www.w3.org/TR/WebCryptoAPI/#rsa-pss ¶21. RSA-PSS]] of w3c spec
    */
  def `RSA-PSS`(
      modulusLength: Long,
      publicExponent: BigInteger,
      hash: HashAlgorithmIdentifier): RsaHashedKeyAlgorithm =
    apply("RSA-PSS", modulusLength, publicExponent, hash)

  /** see [[http://www.w3.org/TR/WebCryptoAPI/#rsa-pss ¶21. RSA-OAEP]] of w3c spec
    */
  def `RSA-OAEP`(
      modulusLength: Long,
      publicExponent: BigInteger,
      hash: HashAlgorithmIdentifier): RsaHashedKeyAlgorithm =
    apply("RSA-OAEP", modulusLength, publicExponent, hash)
}

@js.native
trait RsaHashedImportParams extends KeyAlgorithm {
  var hash: HashAlgorithmIdentifier = js.native
}

object RsaHashedImportParams {
  @inline
  def apply(name: String, hash: HashAlgorithmIdentifier): RsaHashedImportParams =
    js.Dynamic
      .literal(name = name, hash = hash.asInstanceOf[js.Any])
      .asInstanceOf[RsaHashedImportParams]
}

// RSA-PSS

@js.native
trait RsaPssParams extends Algorithm {
  var saltLength: Double = js.native
}

object RsaPssParams {
  @inline
  def apply(name: String, saltLength: Long): RsaPssParams =
    js.Dynamic
      .literal(name = name, saltLength = saltLength.toDouble)
      .asInstanceOf[RsaPssParams]
}

// RSA-OAEP

@js.native
trait RsaOaepParams extends Algorithm {
  var label: BufferSource = js.native
}

object RsaOaepParams {
  @inline
  def apply(name: String, label: BufferSource): RsaOaepParams =
    js.Dynamic.literal(name = name, label = label).asInstanceOf[RsaOaepParams]
}

// ECDSA

@js.native
trait EcdsaParams extends Algorithm {
  var hash: HashAlgorithmIdentifier = js.native
}

object EcdsaParams {
  @inline
  def apply(name: String, hash: HashAlgorithmIdentifier): EcdsaParams =
    js.Dynamic
      .literal(name = name, hash = hash.asInstanceOf[js.Any])
      .asInstanceOf[EcdsaParams]
}

@js.native
trait EcKeyGenParams extends Algorithm {
  var namedCurve: String = js.native
}

object EcKeyGenParams {
  @inline
  def apply(name: String, namedCurve: String): EcKeyGenParams =
    js.Dynamic
      .literal(name = name, namedCurve = namedCurve)
      .asInstanceOf[EcKeyGenParams]
}

@js.native
trait EcKeyAlgorithm extends KeyAlgorithm {
  var namedCurve: String = js.native
}

object EcKeyAlgorithm {
  @inline
  def apply(name: String, namedCurve: String): EcKeyAlgorithm =
    js.Dynamic
      .literal(name = name, namedCurve = namedCurve)
      .asInstanceOf[EcKeyAlgorithm]
}

@js.native
trait EcKeyImportParams extends KeyAlgorithm {
  var namedCurve: String = js.native
}

object EcKeyImportParams {
  @inline
  def apply(name: String, namedCurve: String): EcKeyImportParams =
    js.Dynamic
      .literal(name = name, namedCurve = namedCurve)
      .asInstanceOf[EcKeyImportParams]
}

// ECDH

@js.native
trait EcdhKeyDeriveParams extends KeyAlgorithm {
  var `public`: CryptoKey = js.native
}

object EcdhKeyDeriveParams {
  @inline
  def apply(name: String, `public`: CryptoKey): EcdhKeyDeriveParams =
    js.Dynamic
      .literal(name = name, `public` = `public`)
      .asInstanceOf[EcdhKeyDeriveParams]
}

// AES-CTR

@js.native
trait AesCtrParams extends Algorithm {
  var counter: BufferSource = js.native

  var length: Short = js.native
}

object AesCtrParams {
  @inline
  def apply(name: String, counter: BufferSource, length: Short): AesCtrParams =
    js.Dynamic
      .literal(name = name, counter = counter, length = length)
      .asInstanceOf[AesCtrParams]
}

@js.native
trait AesKeyAlgorithm extends KeyAlgorithm {
  var length: Int = js.native
}

object AesKeyAlgorithm {
  @inline
  def apply(name: String, length: Short): AesKeyAlgorithm =
    js.Dynamic
      .literal(name = name, length = length)
      .asInstanceOf[AesKeyAlgorithm]
}

@js.native
trait AesKeyGenParams extends KeyAlgorithm {
  var length: Int = js.native
}

object AesKeyGenParams {
  @inline
  def apply(name: String, length: Short): AesKeyGenParams =
    js.Dynamic
      .literal(name = name, length = length)
      .asInstanceOf[AesKeyGenParams]
}

@js.native
trait AesDerivedKeyParams extends KeyAlgorithm {
  var length: Int = js.native
}

object AesDerivedKeyParams {
  @inline
  def apply(name: String, length: Short): AesDerivedKeyParams =
    js.Dynamic
      .literal(name = name, length = length)
      .asInstanceOf[AesDerivedKeyParams]
}

// AES-CBC

@js.native
trait AesCbcParams extends Algorithm {
  var iv: BufferSource = js.native
}

object AesCbcParams {
  @inline
  def apply(name: String, iv: BufferSource): AesCbcParams =
    js.Dynamic.literal(name = name, iv = iv).asInstanceOf[AesCbcParams]
}

// AES-CMAC

@js.native
trait AesCmacParams extends Algorithm {
  var length: Int = js.native
}

object AesCmacParams {
  @inline
  def apply(name: String, length: Int): AesCmacParams =
    js.Dynamic
      .literal(name = name, length = length)
      .asInstanceOf[AesCmacParams]
}

// AES-GCM

@js.native
trait AesGcmParams extends Algorithm {
  var iv: BufferSource = js.native

  var additionalData: BufferSource = js.native

  var tagLength: Short = js.native
}

object AesGcmParams {
  @inline
  def apply(
      name: String,
      iv: BufferSource,
      additionalData: BufferSource,
      tagLength: Short): AesGcmParams =
    js.Dynamic
      .literal(name = name, iv = iv, additionalData = additionalData, tagLength = tagLength)
      .asInstanceOf[AesGcmParams]
}

// AES-CFB

@js.native
trait AesCfbParams extends Algorithm {
  var iv: BufferSource = js.native
}

object AesCfbParams {
  @inline
  def apply(name: String, iv: BufferSource): AesCfbParams =
    js.Dynamic.literal(name = name, iv = iv).asInstanceOf[AesCfbParams]
}

// AES-KW

// HMAC

@js.native
trait HmacImportParams extends Algorithm {
  var hash: HashAlgorithmIdentifier = js.native

  var length: Double = js.native
}

object HmacImportParams {
  @inline
  def apply(name: String, hash: HashAlgorithmIdentifier, length: Long): HmacImportParams =
    js.Dynamic
      .literal(name = name, hash = hash.asInstanceOf[js.Any], length = length.toDouble)
      .asInstanceOf[HmacImportParams]
}

@js.native
trait HmacKeyAlgorithm extends KeyAlgorithm {
  var hash: HashAlgorithmIdentifier = js.native

  var length: Double = js.native
}

object HmacKeyAlgorithm {
  @inline
  def apply(name: String, hash: HashAlgorithmIdentifier, length: Long): HmacKeyAlgorithm =
    js.Dynamic
      .literal(name = name, hash = hash.asInstanceOf[js.Any], length = length.toDouble)
      .asInstanceOf[HmacKeyAlgorithm]
}

@js.native
trait HmacKeyGenParams extends KeyAlgorithm {
  var hash: HashAlgorithmIdentifier = js.native

  var length: Double = js.native
}

object HmacKeyGenParams {
  @inline
  def apply(name: String, hash: HashAlgorithmIdentifier, length: Long): HmacKeyGenParams =
    js.Dynamic
      .literal(name = name, hash = hash.asInstanceOf[js.Any], length = length.toDouble)
      .asInstanceOf[HmacKeyGenParams]
}

// Diffie-Hellman

@js.native
trait DhKeyGenParams extends Algorithm {
  var prime: BigInteger = js.native

  var generator: BigInteger = js.native
}

object DhKeyGenParams {
  @inline
  def apply(name: String, prime: BigInteger, generator: BigInteger): DhKeyGenParams =
    js.Dynamic
      .literal(name = name, prime = prime, generator = generator)
      .asInstanceOf[DhKeyGenParams]
}

@js.native
trait DhKeyAlgorithm extends KeyAlgorithm {
  var prime: BigInteger = js.native

  var generator: BigInteger = js.native
}

object DhKeyAlgorithm {
  @inline
  def apply(name: String, prime: BigInteger, generator: BigInteger): DhKeyAlgorithm =
    js.Dynamic
      .literal(name = name, prime = prime, generator = generator)
      .asInstanceOf[DhKeyAlgorithm]
}

@js.native
trait DhKeyDeriveParams extends Algorithm {
  var `public`: CryptoKey = js.native
}

object DhKeyDeriveParams {
  @inline
  def apply(name: String, public: CryptoKey): DhKeyDeriveParams =
    js.Dynamic
      .literal(name = name, public = public)
      .asInstanceOf[DhKeyDeriveParams]
}

@js.native
trait DhImportKeyParams extends Algorithm {
  var prime: BigInteger = js.native

  var generator: BigInteger = js.native
}

object DhImportKeyParams {
  @inline
  def apply(name: String, prime: BigInteger, generator: BigInteger): DhImportKeyParams =
    js.Dynamic
      .literal(name = name, prime = prime, generator = generator)
      .asInstanceOf[DhImportKeyParams]
}

// CONCAT

@js.native
trait ConcatParams extends Algorithm {
  var hash: HashAlgorithmIdentifier = js.native

  var algorithmId: BufferSource = js.native

  var partyUInfo: BufferSource = js.native

  var partyVInfo: BufferSource = js.native

  var publicInfo: BufferSource = js.native

  var privateInfo: BufferSource = js.native
}

object ConcatParams {
  @inline
  def apply(
      name: String,
      hash: HashAlgorithmIdentifier,
      algorithmId: BufferSource,
      partyUInfo: BufferSource,
      partyVInfo: BufferSource,
      publicInfo: BufferSource,
      privateInfo: BufferSource): ConcatParams =
    js.Dynamic
      .literal(
        name = name,
        hash = hash.asInstanceOf[js.Any],
        algorithmId = algorithmId,
        partyUInfo = partyUInfo,
        partyVInfo = partyVInfo,
        publicInfo = publicInfo,
        privateInfo = privateInfo
      )
      .asInstanceOf[ConcatParams]
}

// HKDF-CTR

@js.native
trait HkdfCtrParams extends Algorithm {
  var hash: HashAlgorithmIdentifier = js.native

  var label: BufferSource = js.native

  var context: BufferSource = js.native
}

object HkdfCtrParams {
  @inline
  def apply(
      name: String,
      hash: HashAlgorithmIdentifier,
      label: BufferSource,
      context: BufferSource): HkdfCtrParams =
    js.Dynamic
      .literal(name = name, hash = hash.asInstanceOf[js.Any], label = label, context = context)
      .asInstanceOf[HkdfCtrParams]
}

// PBKDF2

@js.native
trait Pbkdf2Params extends HashAlgorithm {
  var salt: BufferSource = js.native

  var iterations: Double = js.native

  var hash: HashAlgorithmIdentifier = js.native
}

object Pbkdf2Params {
  @inline
  def apply(
      name: String,
      salt: BufferSource,
      iterations: Long,
      hash: HashAlgorithmIdentifier): Pbkdf2Params =
    js.Dynamic
      .literal(
        name = name,
        salt = salt,
        iterations = iterations.toDouble,
        hash = hash.asInstanceOf[js.Any])
      .asInstanceOf[Pbkdf2Params]
}

/** See [[http://www.w3.org/TR/WebCryptoAPI/#cryptokey-interface ¶ 13. CryptoKey Interface]] of w3c
  * spec
  */
@js.native
trait KeyUsage extends js.Any

object KeyUsage {
  val encrypt = "encrypt".asInstanceOf[KeyUsage]
  val decrypt = "decrypt".asInstanceOf[KeyUsage]
  val sign = "sign".asInstanceOf[KeyUsage]
  val verify = "verify".asInstanceOf[KeyUsage]
  val deriveKey = "deriveKey".asInstanceOf[KeyUsage]
  val deriveBits = "deriveBits".asInstanceOf[KeyUsage]
  val wrapKey = "wrapKey".asInstanceOf[KeyUsage]
  val unwrapKey = "unwrapKey".asInstanceOf[KeyUsage]
}

/** see [[http://www.w3.org/TR/WebCryptoAPI/#cryptokey-interface ¶13 CryptoKey interface]] in W3C
  * doc
  */
@js.native
trait KeyType extends js.Any

object KeyType {
  val public = "public".asInstanceOf[KeyType]
  val `private` = "private".asInstanceOf[KeyType]
  val secret = "secret".asInstanceOf[KeyType]
}

/** see [[http://www.w3.org/TR/WebCryptoAPI/#dfn-KeyFormat ¶14.2 Data Types]] in W3C spec
  */
@js.native
trait KeyFormat extends js.Any

object KeyFormat {

  /** An unformatted sequence of bytes. Intended for secret keys. */
  val raw = "raw".asInstanceOf[KeyFormat]

  /** The DER encoding of the PrivateKeyInfo structure from RFC 5208. */
  val pkcs8 = "pkcs8".asInstanceOf[KeyFormat]

  /** The DER encoding of the SubjectPublicKeyInfo structure from RFC 5280. */
  val spki = "spki".asInstanceOf[KeyFormat]

  /** The key is a JsonWebKey dictionary encoded as a JavaScript object */
  val jwk = "jwk".asInstanceOf[KeyFormat]
}

//
// Todo: fill in the full list of types defined in JSON Web Key (JWK) RFC
// http://tools.ietf.org/html/rfc7517
//

/** see example http://tools.ietf.org/html/rfc7517#appendix-A.1 //todo: where is the specification
  * of n and e?
  */
@js.native
trait RSAPublicKey extends js.Object {

  /* modulus, as a base64 URL encoded String */
  def n: String

  /* exponent, as a base64 URL encoded String */
  def e: String
}
