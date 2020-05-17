/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

/**
  * This package is the start of a multipart implementation for http4s.
  * It is still deficient in a few ways:
  *
  * - All encoding is chunked transfers, except for entities small
  * enough to fit into the blaze buffer.  This irritates some server
  * implementations.
  *
  * - When decoding, chunks are kept in memory.  Large ones should be
  * buffered to a temp file.
  *
  * - It's a bit handwavy around character sets.  Things probably go
  * horribly wrong if you're not UTF-8.
  *
  * - This module is lightly tested, and its API should be considered
  * experimental.
  *
  * Enter this package at your own risk, but we'd love the feedback.
  */
package object multipart
