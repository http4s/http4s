package org.http4s.netty.utils

import org.http4s.netty.spdy.AbstractStream

/**
 * @author Bryce Anderson
 *         Created on 12/15/13
 */

abstract class SpdyStreamContext[S <: AbstractStream](val spdyversion: Int, isserver: Boolean)
        extends StreamContext[S](isserver)
