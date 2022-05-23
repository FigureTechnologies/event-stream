# Event stream client for Provenance blockchain

This is a flow based project to create an event listener on the [Provenance](https://provenance.io) blockchain and receive block information. 

## Status

[![Latest Release][release-badge]][release-latest]
[![Maven Central][maven-badge]][maven-url]
[![Apache 2.0 License][license-badge]][license-url]
[![Code Coverage][code-coverage-badge]][code-coverage-report]
[![LOC][loc-badge]][loc-report]

[license-badge]: https://img.shields.io/github/license/provenance-io/event-stream.svg
[license-url]: https://github.com/provenance-io/event-stream/blob/main/LICENSE
[maven-badge]: https://maven-badges.herokuapp.com/maven-central/io.provenance.eventstream/es-core/badge.svg
[maven-url]: https://maven-badges.herokuapp.com/maven-central/io.provenance.eventstream/es-core
[release-badge]: https://img.shields.io/github/tag/provenance-io/event-stream.svg
[release-latest]: https://github.com/provenance-io/event-stream/releases/latest
[code-coverage-badge]: https://codecov.io/gh/provenance-io/event-stream/branch/main/graph/badge.svg
[code-coverage-report]: https://app.codecov.io/gh/provenance-io/event-stream
[loc-badge]: https://tokei.rs/b1/github/provenance-io/event-stream
[loc-report]: https://github.com/provenance-io/event-stream


## Installation

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.provenance.eventstream</groupId>
        <artifactId>es-core</artifactId>
        <version>${version}</version>
    </dependency>
    <dependency>
        <groupId>io.provenance.eventstream</groupId>
        <artifactId>es-api</artifactId>
        <version>${version}</version>
    </dependency>
    <dependency>
        <groupId>io.provenance.eventstream</groupId>
        <artifactId>es-api-model</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

### Gradle

#### Groovy

In `build.gradle`:

```groovy
implementation 'io.provenance.eventstream:es-core:${version}'
implementation 'io.provenance.eventstream:es-api:${version}'
implementation 'io.provenance.eventstream:es-api-model:${version}'
```

#### Kotlin

In `build.gradle.kts`:

```kotlin
implementation("io.provenance.eventstream:es-core:${version}")
implementation("io.provenance.eventstream:es-api:${version}")
implementation("io.provenance.eventstream:es-api-model:${version}")
```

## Setup

To get started using the provenance event stream library you need to create an httpAdapter
that will create both the rpc client and the websocket client to your query node of choice. 

*The protocol is required on the host value and can be one of* `http | https | tcp | tcp+tls`. 


```kotlin
val host = "https://rpc.test.provenance.io"
val netAdapter = okHttpNetAdapter(host)
```

With this adapter we can create streams for live data, historical data, metadata, or any combinations. 

## Usage

### Historical Flows 

Historical flows require a `fromHeight` parameter where you want your stream to start.

Optionally, you can add `toHeight` as an optional parameter. If not supplied the stream will go to current block height.

Get block header flows: 
```kotlin
val log = KotlinLogging.logger {}

historicalBlockHeaderFlow(netAdapter, 1, 100)
  .onEach { log.info { "oldHeader: ${it.height}" } }
  .collect()
```

Get block data flows: 
```kotlin
val log = KotlinLogging.logger {}

historicalBlockDataFlow(netAdapter, 1, 100)
  .onEach { log.info { "oldBlock: ${it.height}" } }
  .collect()
```

### Live Flows: 
Live flows require an adapter to decode the JSON responses from the chain. 

The project includes a `moshi` adapter configured to decode the RPC responses 

Get live block headers:
```kotlin
val log = KotlinLogging.logger {}
val decoderAdapter = moshiDecoderAdapter()

liveBlockHeaderFlow(netAdapter, decoderAdapter)
  .onEach { log.info { "liveHeader: ${it.height}" } }
  .collect()
```

Get live block datas: 
```kotlin
val log = KotlinLogging.logger {}
val decoderAdapter = moshiDecoderAdapter()

liveBlockDataFlow(netAdapter, decoderAdapter)
    .onEach { log.info { "liveBlock: $it" } }
    .collect()
```

### Combinations

These flows can also be combined to create historical + live flows

Get block headers:
```kotlin
val log = KotlinLogging.logger {}

// get the current block height from the node
val current = netAdapter.rpcAdapter.getCurrentHeight()!!
val decoderAdapter = moshiDecoderAdapter()

blockHeaderFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
    .onEach { log.info { "received: ${it.height}" } }
    .collect()
```

Get block datas:
```kotlin
val log = KotlinLogging.logger {}

// get the current block height from the node
val current = netAdapter.rpcAdapter.getCurrentHeight()!!
val decoderAdapter = moshiDecoderAdapter()

blockDataFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
    .onEach { log.info { "received: ${it.height}" } }
    .collect()
```

### Node Subscriptions

We can additionally subscribe to certain events on the node. 

Currently, only `MessageType.NewBlock` and `MessageType.NewBlockHeader` are supported. 

```kotlin
val log = KotlinLogging.logger {}
val decoderAdapter = moshiDecoderAdapter()

nodeEventStream<MessageType.NewBlock>(netAdapter, decoderAdapter)
    .onEach { log.info {"liveBlock: $it" } }
    .collect()
```
