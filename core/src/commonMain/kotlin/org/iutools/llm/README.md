# `org.iutools.llm` -- talking to, and managing, large language models

This folder contains everything that communicates with an LLM or manages one: building the
request, calling the provider, parsing the reply, tracking usage and cost,
and holding the API key. No UI -- screens that need this call in from an
app module.

The code here aims to be vendor-agnostic -- not tied to any one LLM
provider.

Note that this folder is in flux as of 2026-09-07. We are in the process of moving several classes from app-centric package, to this core, neutral package.
