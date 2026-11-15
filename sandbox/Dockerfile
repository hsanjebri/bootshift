# Bootshift build sandbox (README sections 13 and 16).
#
# Temurin 17, 21 and 25 with Maven 3.9.16. Bootshift picks the JDK per project by setting
# JAVA_HOME to /opt/jdk/<version>. The container runs as uid 1000 with a read-only root:
# only /workspace (this run's working copy), /m2 (this run's Maven cache) and /tmp are writable.
# Maven reaches artifacts only through the proxy in BOOTSHIFT_MAVEN_MIRROR; the network has no
# other way out.
#
#   docker build -t bootshift-sandbox:dev sandbox

FROM eclipse-temurin:17.0.21_9-jdk-noble AS jdk17
FROM eclipse-temurin:21.0.13_9-jdk-noble AS jdk21

FROM maven:3.9.16-eclipse-temurin-25-noble

COPY --from=jdk17 /opt/java/openjdk /opt/jdk/17
COPY --from=jdk21 /opt/java/openjdk /opt/jdk/21
# uid 1000 is the base image's "ubuntu" user. Java takes user.home from /etc/passwd, not $HOME,
# so its home moves to the writable /tmp; tools such as JGit write config files there.
RUN ln -s /opt/java/openjdk /opt/jdk/25 \
 && mkdir -p /workspace /m2 \
 && chown 1000:1000 /workspace /m2 \
 && usermod --home /tmp ubuntu

# Replaces Maven's global settings: one mirror for every repository, and the cache in /m2.
COPY settings.xml /usr/share/maven/conf/settings.xml

# The base image points MAVEN_CONFIG at /root/.m2 and wraps mvn in an entrypoint that copies
# files into it. Neither works with a read-only root, so both are cleared.
ENV HOME=/tmp \
    MAVEN_CONFIG= \
    JAVA_HOME=/opt/jdk/25
ENTRYPOINT []

USER 1000:1000
WORKDIR /workspace
CMD ["mvn", "--version"]
