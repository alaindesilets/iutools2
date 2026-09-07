# `org.iutools.llm` -- talking to, and managing, large language models

Everything that communicates with an LLM or manages one: building the
request, calling the provider, parsing the reply, tracking usage and cost.
No UI -- screens that need this call in from an app module.

The design is vendor-neutral: `LlmClient` is an interface,
`GuessMeaningEngine` and the prompt/response helpers know nothing about
which provider answers. `LlmClient_Anthropic` is the one concrete
implementation (Anthropic's Java SDK); it is the only file here that pulls
in an external client library, and the only reason `:core` depends on the
Anthropic SDK at all. A different provider, or a test fake, is just another
`LlmClient`.

(This is why there is no separate module for the LLM call: `:core` is a
plain Kotlin/JVM library, so a plain-jar dependency like the Anthropic SDK
lives here directly. An earlier `:enrichment` module existed only to work
around a Kotlin-Multiplatform limitation that no longer applies -- see
`doc/dev/plans/drop-kmp-core.md`.)
