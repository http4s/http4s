/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.oauth1

/** Representation of a Consumer key and secret */
final case class Consumer(key: String, secret: String)

/** Representation of an OAuth Token and Token secret */
final case class Token(value: String, secret: String)
