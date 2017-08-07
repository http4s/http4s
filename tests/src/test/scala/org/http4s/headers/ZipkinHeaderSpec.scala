package org.http4s
package headers

import org.http4s.Http4sSpec

import scalaz.\/

class ZipkinHeaderSpec extends HeaderLaws {
  checkAll("X-B3-Sampled", headerLaws(`X-B3-Sampled`))
  checkAll("X-B3-Flags", headerLaws(`X-B3-Flags`))
  checkAll("X-B3-TraceId", headerLaws(`X-B3-TraceId`))
  checkAll("X-B3-SpanId", headerLaws(`X-B3-SpanId`))
  checkAll("X-B3-ParentSpanId", headerLaws(`X-B3-ParentSpanId`))

  "flags" >> {
    import `X-B3-Flags`.Flag
    "no parse when arbitrary string" >> {
      `X-B3-Flags`.parse("asdf") must be_-\/
    }
    "no parse when negative" >> {
      `X-B3-Flags`.parse("-1") must be_-\/
    }

    "parses no flags when 0" >> {
      val noFlags = "0"
      `X-B3-Flags`.parse(noFlags) must_=== {
        \/.right(`X-B3-Flags`(Set.empty))
      }
    }
    "parses 'debug' flag" >> {
      val debugFlag = "1"
      `X-B3-Flags`.parse(debugFlag) must_=== {
        \/.right(`X-B3-Flags`(Set(Flag.Debug)))
      }
    }
    "parses 'sampling set' flag" >> {
      val samplingSetFlag = "2"
      `X-B3-Flags`.parse(samplingSetFlag) must_=== {
        \/.right(`X-B3-Flags`(Set(Flag.SamplingSet)))
      }
    }
    "parses 'sampled' flag" >> {
      val sampledFlag = "4"
      `X-B3-Flags`.parse(sampledFlag) must_=== {
        \/.right(`X-B3-Flags`(Set(Flag.Sampled)))
      }
    }
    "parses multiple flags" >> {
      val sampledAndDebugFlag = "5"
      val result = `X-B3-Flags`.parse(sampledAndDebugFlag)
      result must be_\/-
      result.toOption.get.flags must contain(Flag.Sampled)
      result.toOption.get.flags must contain(Flag.Debug)
    }

    "renders when no flags" >> {
      val result = `X-B3-Flags`(Set.empty).value
      result must_=== fv"0"
    }
    "renders when one flag" >> {
      val result = `X-B3-Flags`(Set(Flag.Debug)).value
      result must_=== fv"1"
    }
    "renders when no flags" >> {
      val result = `X-B3-Flags`(Set.empty).value
      result must_=== fv"0"
    }
    "renders when multiple flags" >> {
      val result = `X-B3-Flags`(Set(Flag.Debug, Flag.Sampled)).value
      result must_=== fv"5"
    }
  }

  "sampled" >> {
    "parses false when 0" >> {
      `X-B3-Sampled`.parse("0") must_=== {
        \/.right(`X-B3-Sampled`(false))
      }
    }
    "parses true when 1" >> {
      `X-B3-Sampled`.parse("1") must_=== {
        \/.right(`X-B3-Sampled`(true))
      }
    }
    "no parse when not 0 or 1" >> {
      `X-B3-Sampled`.parse("01") must be_-\/
    }
  }

  // The parsing logic is the same for all ids.
  "id" >> {
    "no parse when less than 16 chars" >> {
      val not16Chars = "abcd1234"
      `X-B3-TraceId`.parse(not16Chars) must be_-\/
    }
    "no parse when more than 16 chars" >> {
      val not16Chars = "abcd1234abcd12345"
      `X-B3-TraceId`.parse(not16Chars) must be_-\/
    }
    "no parse when contains non-hex char" >> {
      val containsZ = "abcd1z34abcd1234"
      `X-B3-TraceId`.parse(containsZ) must be_-\/
    }

    "parses a Long when contains 16-char case-insensitive hex string and trailing whitespace" >> {
      val long = 2159330025234698493L
      val hexString = "1dF77B37a2f310fD \t "

      `X-B3-TraceId`.parse(hexString) must_=== {
        \/.right(`X-B3-TraceId`(long))
      }
    }

  }
}
