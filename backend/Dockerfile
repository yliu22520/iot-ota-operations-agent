FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
ARG MAVEN_PROXY_HOST
ARG MAVEN_PROXY_PORT
COPY pom.xml .
RUN if [ -n "${MAVEN_PROXY_HOST}" ] && [ -n "${MAVEN_PROXY_PORT}" ]; then \
        mkdir -p /root/.m2 && \
        printf '%s\n' \
          '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
          '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
          '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
          '  <proxies>' \
          '    <proxy>' \
          '      <id>docker-build-proxy</id>' \
          '      <active>true</active>' \
          '      <protocol>http</protocol>' \
          "      <host>${MAVEN_PROXY_HOST}</host>" \
          "      <port>${MAVEN_PROXY_PORT}</port>" \
          '    </proxy>' \
          '  </proxies>' \
          '</settings>' > /root/.m2/settings.xml; \
    fi
COPY src src
RUN --mount=type=cache,id=ota-maven-repository,target=/root/.m2/repository \
    set -eu; \
    attempts=0; \
    until mvn -q -B \
        -Daether.transport.http.retryHandler.count=5 \
        -Daether.transport.http.retryHandler.interval=2000 \
        -Dmaven.wagon.http.retryHandler.count=5 \
        package -Dmaven.test.skip=true; do \
      attempts=$((attempts + 1)); \
      if [ "${attempts}" -ge 5 ]; then exit 1; fi; \
      echo "Maven package failed; retrying (${attempts}/5)"; \
      sleep 2; \
    done

FROM curlimages/curl:8.12.1 AS knowledge-model
USER root
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ARG KNOWLEDGE_MODEL_REVISION=0e60b8d9d2166d80387f86e3b48ec9ced55f4d15
ARG KNOWLEDGE_MODEL_SHA256=ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665
ARG KNOWLEDGE_TOKENIZER_SHA256=0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39
RUN --mount=type=cache,target=/download \
    export http_proxy="${HTTP_PROXY}" \
           https_proxy="${HTTPS_PROXY}" \
           no_proxy="${NO_PROXY}" \
    && \
    mkdir -p /models/multilingual-e5-small \
    && curl --fail --location --retry 8 --retry-all-errors --continue-at - \
        "https://huggingface.co/intfloat/multilingual-e5-small/resolve/${KNOWLEDGE_MODEL_REVISION}/onnx/model.onnx" \
        --output /download/model.onnx \
    && curl --fail --location --retry 8 --retry-all-errors --continue-at - \
        "https://huggingface.co/intfloat/multilingual-e5-small/resolve/${KNOWLEDGE_MODEL_REVISION}/onnx/tokenizer.json" \
        --output /download/tokenizer.json \
    && cp /download/model.onnx /models/multilingual-e5-small/model.onnx \
    && cp /download/tokenizer.json /models/multilingual-e5-small/tokenizer.json \
    && echo "${KNOWLEDGE_MODEL_SHA256}  /models/multilingual-e5-small/model.onnx" | sha256sum -c \
    && echo "${KNOWLEDGE_TOKENIZER_SHA256}  /models/multilingual-e5-small/tokenizer.json" | sha256sum -c

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/iot-ota-operations-agent-0.1.0-SNAPSHOT.jar app.jar
COPY --from=knowledge-model /models /app/models
ENV KNOWLEDGE_ONNX_MODEL_URI=file:/app/models/multilingual-e5-small/model.onnx \
    KNOWLEDGE_ONNX_TOKENIZER_URI=file:/app/models/multilingual-e5-small/tokenizer.json \
    KNOWLEDGE_ONNX_CACHE_DIRECTORY=/app/models/cache
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
