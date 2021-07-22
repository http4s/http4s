---
menu: error-handling
title: Error handling
weight: 320
---

A `MessageFailure` indicates an error handling an HTTP message.  These
include:

* `ParsingFailure`: indicative of a malformed HTTP message in the
  request line or headers.
* `MalformedMessageBodyFailure`: indicative of a message that has a
  syntactic error in its body.  For example, trying to decode `{
  "broken:"` as JSON will result in a `MalforedMessageBodyFailure`.
* `InvalidMessageBodyFailure`: indicative of a message that is
  syntactically correct, but semantically incorrect.  A well-formed
  JSON request that is missing expected fields may generate this
  failure.
* `MediaTypeMissing`: indiciates that the message had no media type,
  and the server wasn't willing to infer it.
* `MediaTypeMismatch`: indiciates that the server received a media
  type that it wasn't prepared to handle.

## Logging

If a `MessageFailure` is not handled by your HTTP service, it reaches
the backend in the form of a failed task, where it is transformed into
an HTTP response.  To guard against XSS attacks, care is taken in each
of these renderings to not reflect information back from the request.
Diagnostic information about syntax errors or missing fields,
including a full stack trace, is logged to the
`org.http4s.server.message-failures` category at `DEBUG` level.

## Customizing error handling

TODO
