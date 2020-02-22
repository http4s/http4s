# Security Policy

## Supported Versions

We are currently providing security updates to the following versions: 

| Version | Supported          |
| ------- | ------------------ |
| 0.21.x   | :white_check_mark: |
| 0.20.x   | :white_check_mark: |
| 0.19.x   | :x: |
| 0.18.x   | :x: |
| < 0.18   | :x: |

## Reporting a Vulnerability

We will use [keybase](https://keybase.io) as the vehicle for reporting security issues as that gives us a
much better feedback loop. We want to work with the reporter to make sure we understand the reported issue.

If the vulnerability is found to be true we will release a patch version of our software
and make sure that finds it way to Maven Central before we push the patch to github.
After the patch is available on Maven Central, we will also provide a security advisory through github.

We strongly recommend users of our libraries to use [Scala Steward](https://github.com/fthomas/scala-steward) or something similar to 
automatically receive updates.

### Security Maintainer list:

|name | github | keybase |
|-----|--------|---------|
| Ross A. Baker | @rossabaker | @rossabaker|
| Christopher Davenport | @christopherdavenport | @davenpcm |
| Erlend Hamnaberg | @hamnis | @hamnis|  
