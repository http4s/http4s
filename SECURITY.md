# Security Policy

## Supported Versions

We are currently providing security updates to the following versions: 

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| 0.23.x  | :white_check_mark: |
| 0.22.x  | :x: |
| 0.21.x  | :x: |
| 0.20.x  | :x: |
| 0.19.x  | :x: |
| 0.18.x  | :x: |
| < 0.18  | :x: |

## Reporting a Vulnerability

We will use [keybase](https://keybase.io) as the vehicle for reporting security issues as that gives us a
forum to discuss, analyze, and remediate the threat before an exploit is published.
[Responsible disclosure](https://en.wikipedia.org/wiki/Responsible_disclosure) enhances security for the entire community.

If the issue is deemed a vulnerability, we will release a patch version of our software
and make sure that finds it way to Maven Central before we push the patch to github.
After the patch is available on Maven Central, we will also provide a [security advisory](https://github.com/http4s/http4s/security/advisories) through github.
As with every release, the source jars are published to maven central at the same time as the binaries.

We strongly recommend users of our libraries to use [Scala Steward](https://github.com/fthomas/scala-steward) or something similar to 
automatically receive updates.

### Security Maintainer list:

|name | github | keybase |
|-----|--------|---------|
| Ross A. Baker | @rossabaker | @rossabaker|
| Christopher Davenport | @christopherdavenport | @davenpcm |
| Erlend Hamnaberg | @hamnis | @hamnis|  
