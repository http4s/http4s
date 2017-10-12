package org.http4s
package headers

class ZipkinHeaderSpec extends Http4sSpec with HeaderLaws {
  checkAll("X-B3-Sampled", headerLaws(`X-B3-Sampled`))
  checkAll("X-B3-Flags", headerLaws(`X-B3-Flags`))
  checkAll("X-B3-TraceId", headerLaws(`X-B3-TraceId`))
  checkAll("X-B3-SpanId", headerLaws(`X-B3-SpanId`))
  checkAll("X-B3-ParentSpanId", headerLaws(`X-B3-ParentSpanId`))

  "flags" >> {
    import `X-B3-Flags`.Flag
    "no parse when arbitrary string" >> {
      `X-B3-Flags`.parse("asdf") must beLeft
    }
    "no parse when negative" >> {
      `X-B3-Flags`.parse("-1") must beLeft
    }

    "parses no flags when 0" >> {
      val noFlags = "0"
      `X-B3-Flags`.parse(noFlags) must_=== {
        Right(`X-B3-Flags`(Set.empty))
      }
    }
    "parses 'debug' flag" >> {
      val debugFlag = "1"
      `X-B3-Flags`.parse(debugFlag) must_=== {
        Right(`X-B3-Flags`(Set(Flag.Debug)))
      }
    }
    "parses 'sampling set' flag" >> {
      val samplingSetFlag = "2"
      `X-B3-Flags`.parse(samplingSetFlag) must_=== {
        Right(`X-B3-Flags`(Set(Flag.SamplingSet)))
      }
    }
    "parses 'sampled' flag" >> {
      val sampledFlag = "4"
      `X-B3-Flags`.parse(sampledFlag) must_=== {
        Right(`X-B3-Flags`(Set(Flag.Sampled)))
      }
    }
    "parses multiple flags" >> {
      val sampledAndDebugFlag = "5"
      val result = `X-B3-Flags`.parse(sampledAndDebugFlag)
      result must beRight
      result.right.get.flags must contain(Flag.Sampled)
      result.right.get.flags must contain(Flag.Debug)
    }

    "renders when no flags" >> {
      val result = `X-B3-Flags`(Set.empty).value
      result must_=== "0"
    }
    "renders when one flag" >> {
      val result = `X-B3-Flags`(Set(Flag.Debug)).value
      result must_=== "1"
    }
    "renders when no flags" >> {
      val result = `X-B3-Flags`(Set.empty).value
      result must_=== "0"
    }
    "renders when multiple flags" >> {
      val result = `X-B3-Flags`(Set(Flag.Debug, Flag.Sampled)).value
      result must_=== "5"
    }
  }

  "sampled" >> {
    "parses false when 0" >> {
      `X-B3-Sampled`.parse("0") must_=== {
        Right(`X-B3-Sampled`(false))
      }
    }
    "parses true when 1" >> {
      `X-B3-Sampled`.parse("1") must_=== {
        Right(`X-B3-Sampled`(true))
      }
    }
    "no parse when not 0 or 1" >> {
      `X-B3-Sampled`.parse("01") must beLeft
    }
  }

  // The parsing logic is the same for all ids.
  "id" >> {
    "no parse when less than 16 chars" >> {
      val not16Chars = "abcd1234"
      `X-B3-TraceId`.parse(not16Chars) must beLeft
    }
    "no parse when more than 16 but less than 32 chars" >> {
      val not16Or32Chars = "abcd1234a"
      `X-B3-TraceId`.parse(not16Or32Chars) must beLeft
    }
    "no parse when more than 32 chars" >> {
      val not16Or32Chars = "2abcd1234a1493b12"
      `X-B3-TraceId`.parse(not16Or32Chars) must beLeft
    }
    "no parse when contains non-hex char" >> {
      val containsZ = "abcd1z34abcd1234"
      `X-B3-TraceId`.parse(containsZ) must beLeft
    }

    "parses a Long when contains 16-char case-insensitive hex string and trailing whitespace" >> {
      val long = 2159330025234698493L
      val hexString = "1dF77B37a2f310fD \t "
      `X-B3-TraceId`.parse(hexString) must_=== {
        Right(`X-B3-TraceId`(long, None))
      }
    }
    "parses a two Longs when contains 32-char case-insensitive hex string and trailing whitespace" >> {
      val msbLong = 2159330025234698493L
      val lsbLong = 7000848103853419616L
      val hexString = "1dF77B37a2f310fD6128024224a66C60 \t "
      `X-B3-TraceId`.parse(hexString) must_=== {
        Right(`X-B3-TraceId`(msbLong, Some(lsbLong)))
      }
    }
  }
}
