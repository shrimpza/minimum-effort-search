# Minimum Effort Search

A simple REST service exposing some RedisSearch functionality to support
simple self-hosted site search functionality.

## Runtime Requirements

- Java 17 JRE
- A [RediSearch](https://oss.redislabs.com/redisearch/) instance to connect to

## Build

The project is built using Gradle and a Java 17 JDK:

```
$ ./gradlew execJar
```

This generates a fat/uber jar in the `build/libs/` directory which may be
used to run the service.

## Configuration and Running

The service and index schema are configured using a simple YAML config file.

A sample file may be generated by simply running:

```
java -jar minimum-effort-search-exec.jar > config.yml
```

The above will write the file `config.yml` with some example parameters which
you may customise.

Thereafter, run the service with the config file as the first parameter:

```
java -jar minimum-effort-search-exec.jar config.yml
```

The service will start up, listening on the port you specifier in the config
file.

## API

### Add documents to the index

*Add a single document:*

`POST /index/add`

```json
{
  "id": "1",
  "score": 1.0,
  "fields": {
    "title": "Blue T-Shirt",
    "body": "A very basic blue t-shirt you can wear",
    "price": 100,
    "tags": "shirt,blue,clothing"
  }
}
```

*Add multiple documents:*

`POST /index/addBatch`

```json
[
  {
    "id": "2",
    "score": 1.0,
    "fields": {
      "title": "Jean Pant",
      ...
    }
  },
  {
    "id": "3",
    "fields": {
      "title": "Rooi Rokkie",
      ...
    }
  },
]
```

### Search for documents in the index:

`GET /search?q=shirt&limit=10&offset=0`

Parameters:
- `q`: Search query string
- `limit`: Limit the result set to this number of documents
- `offset`: Return documents starting at this offset. In combination with
            `limit`, allows for pagination through results.

```json
{
  "docs": [
    {
      "id": "1",
      "payload": null,
      "score": 1.0,
      "fields": {
        "title": "Blue T-Shirt",
        "body": "A very basic blue t-shirt you can wear",
        "price": 100,
        "tags": "shirt,blue,clothing"
      }
    }
  ],
  "limit": 10,
  "offset": 0,
  "totalResults": 1
}
```
