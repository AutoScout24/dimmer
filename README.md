# Toguru - トグル

[![Build Status](https://travis-ci.org/AutoScout24/toguru.svg?branch=master)](https://travis-ci.org/AutoScout24/toguru)
[![Coverage Status](https://coveralls.io/repos/github/AutoScout24/toguru/badge.svg?branch=master)](https://coveralls.io/github/AutoScout24/toguru?branch=master)
[![Docker Pulls](https://img.shields.io/docker/pulls/as24/toguru.svg)](https://hub.docker.com/r/as24/toguru/)

The toggle guru (Japanese for toggle).

<!-- installing doctoc: https://github.com/thlorenz/doctoc#installation -->
<!-- tocdoc command: doctoc README.md --maxlevel 3 -->
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
<!-- END doctoc generated TOC please keep comment here to allow auto update -->

- [Contributing](#contributing)
- [Management API](#management-api)
  - [Authentication](#authentication)
  - [Error Handling](#error-handling)
- [Getting the Audit Log](#getting-the-audit-log)
- [Getting the current Toggle State](#getting-the-current-toggle-state)
- [Managing Toggles](#managing-toggles)
  - [Creating a Toggle](#creating-a-toggle)
  - [Deleting a Toggle](#deleting-a-toggle)
  - [Getting Toggle data](#getting-toggle-data)
  - [Change Toggle Rollout Percentage](#change-toggle-rollout-percentage)
  - [Disabling a Toggle](#disabling-a-toggle)
- [Related projects](#related-projects)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Contributing

See [contribution documentation](CONTRIBUTING.md).

## Management API

The management API is exposed via a REST interface over http(s).

### Authentication

All management API endpoints requires authentication with an api key. For this,
provide host header with a valid api key in your http requests:

```
Authorization: api-key [your-api-key]
```

with curl, the syntax to add the proper header is

```
curl -H "Authorization: api-key ???" https://your-endpoint.example.com/auditlog
```

### Error Handling

The management API tries to help on how to proceed when a problem occurs (due to
e.g. lack of authentication or a malformed request).  

## Getting the Audit Log

Http method and route: `GET /auditlog`

Payload format: no payload required

Response format:
```
[
  { "id": "toguru-demo-toggle",
    "event": "toggle created",
    "name": "Toguru demo toggle",
    "description": "Toguru demo toggle",
    "tags": { "team": "Toguru team" },
    "meta": {
      "time": "2016-12-20T15:05:46.705Z",
      "epoch": 1482246346705,
      "user": "toguru-team"
    }
  }
]
```

## Getting the current Toggle State

This endpoint is used by the Toguru clients to retrieve the current state of all
toggles.

Http method and route: `GET /togglestate`

Payload format: no payload required

Response format:
```
[
  { "id": "toguru-demo-toggle",
    "tags": { "team": "Toguru team" },
    "rolloutPercentage":20
  }
]
```

## Managing Toggles

### Creating a Toggle

Http method and route: `POST /toggle`

Payload format:
```
{ "name": "Toguru demo toggle",
  "description": "Toguru demo toggle",
  "tags": { "team": "Toguru team" }
}
```

All fields are mandatory. The JSON object under tags may have arbitrary fields,
and must have string fields only. An empty tags map is allowed.

Response format:
```
{ "status":"Ok", "id": "toguru-demo-toggle" }
```

curl example:
```
curl -XPOST https://your-endpoint.example.com/toggle \
  -d '{"name":"Toguru demo toggle","description":"Toguru demo toggle","tags":{"team":"Toguru team"}}'
```

### Deleting a Toggle

Http method and route: `DELETE /toggle/:id`

Payload format: no payload required

Response format:
```
{ "status": "Ok"}
```

curl example:
```
curl -XDELETE https://your-endpoint.example.com/toggle/toguru-demo-toggle
```

### Getting Toggle data

Http method and route: `GET /toggle/:id`

Payload format: no payload required

Response format:
```
{ "id": "toguru-demo-toggle",
  "name": "Toguru demo toggle",
  "description": "Toguru demo toggle",
  "tags": { "team": "Toguru team"} }
```

curl example:
```
curl https://your-endpoint.example.com/toggle/toguru-demo-toggle
```

### Change Toggle Rollout Percentage

Http method and route: `PUT /toggle/:id/globalrollout`

Payload format:
```
{ "percentage": 20 }
```

Response format:
```
{ "status": "Ok"}
```

curl example:
```
curl -XPUT https://your-endpoint.example.com/toggle/toguru-demo-toggle/globalrollout \
  -d '{"percentage":20}'
```

### Disabling a Toggle

Http method and route: `DELETE /toggle/:id/globalrollout`

Payload format: no payload required

Response format:
```
{ "status": "Ok"}
```

curl example:
```
curl -XDELETE https://your-endpoint.example.com/toggle/toguru-demo-toggle/globalrollout
```


## Related projects

* [AutoScout24/toguru-scala-client](https://github.com/AutoScout24/toguru-scala-client): Scala client for this toggling service

## License

See the [LICENSE](LICENSE.md) file for license rights and limitations (MIT).
