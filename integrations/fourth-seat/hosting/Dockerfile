ARG BINARIES=source
FROM node:24.19.0-bookworm-slim AS node
WORKDIR /gateway
COPY integrations/fourth-seat/gateway/package*.json ./
RUN npm ci --omit=dev --ignore-scripts

FROM eclipse-temurin:17.0.20_8-jdk AS source
RUN apt-get update && apt-get install -y --no-install-recommends git maven python3 && rm -rf /var/lib/apt/lists/*
COPY --from=node /usr/local/bin/node /usr/local/bin/node
WORKDIR /build
COPY integrations/nexus/upstream.json integrations/nexus/upstream.json
COPY scripts/build-xmage-bridge.sh scripts/build-xmage-bridge.sh
COPY integrations/fourth-seat/hosting/fetch-sources.py fetch-sources.py
RUN python3 fetch-sources.py
ENV MAVEN_OPTS="-Xmx4g -Dfile.encoding=UTF-8"
RUN bash scripts/build-xmage-bridge.sh
RUN mkdir /output && cp build/nexus/mage-server.zip build/nexus/mage-proxy-1.4.62.jar build/nexus/*LICENSE.txt /output/

FROM scratch AS artifact
COPY build/nexus/mage-server.zip build/nexus/mage-proxy-1.4.62.jar build/nexus/*LICENSE.txt /output/

FROM ${BINARIES} AS binaries

FROM eclipse-temurin:17.0.20_8-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /gateway/node_modules /opt/gateway/node_modules
COPY --from=binaries /output/ /opt/engine/
COPY integrations/fourth-seat/gateway/ /opt/gateway/
COPY integrations/fourth-seat/hosting/start.py /opt/start.py
ENV PORT=8080 DATA_DIR=/data SITE_ORIGIN=https://thefourthseat.net
EXPOSE 8080
HEALTHCHECK --interval=30s --start-period=300s --timeout=5s CMD python3 -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.environ.get('PORT','8080')+'/health',timeout=3)"
CMD ["python3", "/opt/start.py"]
