# Toguru - トグル

[![Build Status](https://travis-ci.org/AutoScout24/toguru.svg?branch=master)](https://travis-ci.org/AutoScout24/toguru)
[![Coverage Status](https://coveralls.io/repos/github/AutoScout24/toguru/badge.svg?branch=master)](https://coveralls.io/github/AutoScout24/toguru?branch=master)
[![Docker Pulls](https://img.shields.io/docker/pulls/as24/toguru.svg)](https://hub.docker.com/r/as24/toguru/)

The toggle guru (Japanese for toggle).

<!-- installing doctoc: https://github.com/thlorenz/doctoc#installation -->
<!-- tocdoc command: doctoc README.md --maxlevel 3 -->
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Contributing](#contributing)
- [Management API](#management-api)
  - [Authentication](#authentication)
  - [Error Handling](#error-handling)
  - [Getting the Audit Log](#getting-the-audit-log)
  - [Getting the current Toggle State](#getting-the-current-toggle-state)
  - [Creating a Toggle](#creating-a-toggle)
  - [Deleting a Toggle](#deleting-a-toggle)
  - [Getting Toggle Data](#getting-toggle-data)
  - [Create Toggle Activation](#create-toggle-activation)
  - [Update Toggle Activation](#update-toggle-activation)
  - [Disabling a Toggle](#disabling-a-toggle)
- [Configuration](#configuration)
  - [Togglestate Endpoint Initialization](#togglestate-endpoint-initialization)
- [Related projects](#related-projects)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Contributing

Contributions are welcome!

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

In the service configuration, `auth.api-keys` must contain an array of objects with fields
`name` and `hash`. The hash must be created with BCrypt, see e.g.
[the test configuration](conf/test.conf).

### Error Handling

The management API tries to help on how to proceed when a problem occurs (due to
e.g. lack of authentication or a malformed request).  

### Getting the Audit Log

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

### Getting the current Toggle State

This endpoint is used by the Toguru clients to retrieve the current state of all
toggles.

Http method and route: `GET /togglestate`

Payload format: no payload required

Response format:
```
[
  { "id": "toguru-demo-toggle",
    "tags": { "team": "Toguru team" },
    "activations": [
      { "rollout": { "percentage": 20 },
        "attributes": {
          "hair": [ "black", "white" ]
        }
      }
    ]
  }
]
```

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

### Getting Toggle Data

Http method and route: `GET /toggle/:id`

Payload format: no payload required

Response format:
```
{ "id": "toguru-demo-toggle",
  "name": "Toguru demo toggle",
  "description": "Toguru demo toggle",
  "tags": { "team": "Toguru team" },
  "activations": [
    { "attributes": { "hair": [ "black", "white" ] },
      "rollout": { "percentage": 20 }
  ] }
```

curl example:
```
curl https://your-endpoint.example.com/toggle/toguru-demo-toggle
```

### Create Toggle Activation

To activate a toggle, you need to create a toggle activation that defines
the rollout percentage and additional attribute-based constraints that
the ClientInfo must have in order for the toggle to switched on.

Http method and route: `POST /toggle/:id/activations`

Payload format:
```
{ "rollout": { "percentage": 20 },
  "attributes": { "culture": [ "de-DE", "DE"] , ... } }
```

Rollout and attributes are both optional, fields in attributes can be defined
as needed, values of attribute fields can be strings or array of strings.

Response format:
```
{ "status": "Ok", "index": 0 }
```

curl example:
```
curl -XPOST https://your-endpoint.example.com/toggle/toguru-demo-toggle/activations \
  -d '{ "rollout": { "percentage": 20 }, "attributes": { "culture": "de-DE" } }'
```

### Update Toggle Activation

Http method and route: `PUT /toggle/:id/activations/0`

Payload format:
```
{ "rollout": { "percentage": 20 }, "attributes": { "hair": "black" } }
```

Rollout and attributes are both optional, fields in attributes can be defined
as needed, values of attribute fields can be strings or array of strings.

Response format:
```
{ "status": "Ok"}
```

curl example:
```
curl -XPUT https://your-endpoint.example.com/toggle/toguru-demo-toggle/activations/0 \
  -d '{ "rollout": { "percentage": 20 }, "attributes": { "hair": ["black", "white"] } }'
```

### Disabling a Toggle

Disabling a toggle is done by deleting its activation.

Http method and route: `DELETE /toggle/:id/activations/0`

Payload format: no payload required

Response format:
```
{ "status": "Ok"}
```

curl example:
```
curl -XDELETE https://your-endpoint.example.com/toggle/toguru-demo-toggle/activations/0
```

## Configuration

### Togglestate Endpoint Initialization

When starting a toguru server, the toggle state endpoint will initially return
an outdated toggle state. This is because the ToggleState actor needs to
replay all toggle state changes before it is serving the recent toggle state.

In order to prevent serving this stale state, the configuration setting
`toggleState.initializeOnStartup` is set to `true` by default. This setting
also causes the health check to fail until the toggle state endpoint is fully
initialized.

To switch both behaviours off, set `toggleState.initializeOnStartup` to `false`.

## Related projects

* [AutoScout24/toguru-scala-client](https://github.com/AutoScout24/toguru-scala-client): Scala client for this toggling service
* [AutoScout24/toguru-panel](https://github.com/AutoScout24/toguru-panel): UI panel for toggle management

## License

See the [LICENSE](LICENSE.md) file for license rights and limitations (MIT).
