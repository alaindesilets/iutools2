# Base: official Microsoft devcontainers image with JDK 21 + a non-root
# "vscode" user already set up — chosen over Anthropic's node:20 reference
# base because this project's primary toolchain is Kotlin/Gradle/Android,
# not Node. The Claude Code CLI itself is added below via the official
# devcontainer Feature (see devcontainer.json's "features" block), which
# installs its own Node.js runtime automatically since this base image
# doesn't ship one.
FROM mcr.microsoft.com/devcontainers/java:21-bookworm

ARG TZ
ENV TZ="$TZ"

# Tools needed by init-firewall.sh (iptables/ipset/dig/aggregate/jq) plus
# general dev conveniences. `aggregate` (for collapsing CIDR ranges) isn't
# in Debian's default repos under that exact package name on all releases;
# if this fails on first build, replace with the `netaddr`-based aggregate6
# equivalent or drop the GitHub-IP-range aggregation step in the firewall
# script and allow the ranges unaggregated (more ipset entries, same effect).
RUN apt-get update && apt-get install -y --no-install-recommends \
  iptables \
  ipset \
  iproute2 \
  dnsutils \
  aggregate \
  jq \
  unzip \
  gh \
  hfst \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# --- Android SDK ---
# Installed to /opt/android-sdk, which devcontainer.json mounts as a named
# volume (android-sdk-${devcontainerId}) so it survives container rebuilds
# without a multi-hundred-MB re-download every time.
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
  && curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
  && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
  && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
  && rm /tmp/cmdline-tools.zip \
  && chown -R vscode:vscode "${ANDROID_HOME}"

# compileSdk/platform + build-tools version below should match what
# core/build.gradle.kts and composeApp/build.gradle.kts actually declare
# (compileSdk = 37 as of this writing) — check both files if this ever
# drifts. Note the package name is "platforms;android-37.0", not
# "platforms;android-37": API 37 isn't a stable release yet, so Google
# distributes it as dotted sub-versions (37.0, 37.1, 37.2-betaN); confirm
# via `sdkmanager --list` if this ever needs bumping.
USER vscode
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
  && sdkmanager --install \
    "platform-tools" \
    "platforms;android-37.0" \
    "build-tools;36.0.0"
USER root

# Copy and set up the firewall script (see init-firewall.sh for the
# allowlisted domains — adapted from Anthropic's reference container to
# add Maven Central / Gradle / Google's Maven, which this project's builds
# need and the original list didn't include).
#
# Deliberately NOT installed as /usr/local/bin/init-firewall.sh: the
# ghcr.io/anthropics/devcontainer-features/claude-code feature (see
# devcontainer.json) unconditionally installs its own generic
# init-firewall.sh at that exact path as one of its build steps, which
# runs *after* this Dockerfile stage — so anything placed there here gets
# silently clobbered by the feature on every build, cache or no cache.
# Using a distinct filename sidesteps the collision instead of fighting
# the feature's build-stage ordering.
COPY init-firewall.sh /usr/local/bin/project-init-firewall.sh
RUN chmod +x /usr/local/bin/project-init-firewall.sh && \
  echo "vscode ALL=(root) NOPASSWD: /usr/local/bin/project-init-firewall.sh" > /etc/sudoers.d/vscode-firewall && \
  chmod 0440 /etc/sudoers.d/vscode-firewall

# The mcr.microsoft.com/devcontainers/java base image grants vscode
# unrestricted passwordless root (/etc/sudoers.d/vscode: "NOPASSWD:ALL") as a
# dev-convenience default. That defeats the firewall above, since vscode
# could just `sudo iptables -F` it away — remove it so vscode's only
# passwordless root path is the one line above.
RUN rm -f /etc/sudoers.d/vscode

# Persist shell history the same way the reference container does.
RUN mkdir -p /commandhistory && \
  echo "export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" >> /home/vscode/.bashrc && \
  touch /commandhistory/.bash_history && \
  chown -R vscode:vscode /commandhistory /home/vscode/.bashrc

# claude-code-config-${devcontainerId} and gradle-cache-${devcontainerId}
# (devcontainer.json's "mounts") are seeded from whatever ownership these
# paths have in the image at container creation. Without this, the named
# volumes come up root-owned and vscode can't write its own login
# credentials (.claude) or Gradle wrapper/dependency cache (.gradle).
RUN mkdir -p /home/vscode/.claude /home/vscode/.gradle && \
  chown -R vscode:vscode /home/vscode/.claude /home/vscode/.gradle

USER vscode
WORKDIR /workspace
