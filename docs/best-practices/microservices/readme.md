# Microservices Best Practices

As described on [Wikipedia](https://en.wikipedia.org/wiki/Microservices):
*Microservices are small, independent processes that communicate with each other
to form complex applications which utilize language-agnostic APIs. These services
are small building blocks, highly decoupled and focused on doing a small task,
facilitating a modular approach to system-building. The microservices architectural
style is becoming the standard for building continuously deployed systems.*

This document provides guidelines for building interoperable applications
in a service-oriented architecture. In short: HTTP/REST service APIs with a JSON wire format.

- [Microservices Best Practices](#microservices-best-practices)
  - [HTTP/REST-Based Service APIs](#httprest-based-service-apis)
  - [Java Server Implementation](#java-server-implementation)
  - [Java Client Implementation](#java-client-implementation)
  - [Java Project Layout](#java-project-layout)
  - [Front-End-Serving Services](#front-end-serving-services)
  - [Deployment](#deployment)

## HTTP/REST-Based Service APIs

**Complex-type parameters: use JSON as wire-format**

Complex-type parameters in PUT/POST/DELETE requests are given as JSON documents.

**Sensitive information: not in the URL**

URLs must not contain sensitive information since they often get logged
by server and client applications.

**Return values: use JSON as wire-format**

Every return value is a valid JSON document. Since JSON support for
simple types is still brittle, we recommend wrapping simple types in a
JSON document, i.e., return `{"name": "foo"}` rather than `"foo"`.

Do not return root-level arrays, as it's an attack vector. See
[here](http://haacked.com/archive/2009/06/25/json-hijacking.aspx/).

**Optional parameters: as URL query parameters**

Optional parameters (with simple type) are given as an unordered list of
query parameters where an omitted parameter indicates absence, e.g.,
`GET /resources/{resourceName}?order={resourceOrder}`. The interface
must specify the default value used in case of absence.

**URL query parameters: camelCased**

URL query parameters are camelCased as in
`GET /resources/{resourceName}?order={resourceOrder}` rather than snake_cased
as in `GET /resources/{resourceName}?order={resource_order}`. This choice is
due to the predominance of Java-based service implementations.

**REST: naming endpoints**

It's usually fine to first think of method names in a regular programming
language (`getResource(String resourceName)` or
`putResource(String resourceName, Resource resource)`) and then
translate them into URL equivalents in a natural way (e.g.,
`{GET | POST | PUT} /resources/{resourceName}`)
while respecting the canonical HTTP method semantics: GET for
retrieving, POST/PUT for submitting and updating, DELETE for deleting
information.

**Primitive required parameters: usually in the URL query path**

Required parameters with simple type are ordered and specified as part
of the URL query path, for example: `GET /resources/{resourceName}` or
`POST /resources/{resourceName}`. In order to make URLs more legible
(and more RESTish, arguably), variables names usually translates to
constant path segments; for example, a method
`getResourceOnBranch(String branch, String resourceName)` could
translate to `GET /branches/{branch}/resources/{resourceName}`

**Error handling**

Standard HTTP mechanisms for error handling must be used. Successful
queries have response code 2xx, absence is indicated by 204 or 404,
authentication issues return 401/403, etc.

Error code 204 should be used for "expected absence", i.e., for
`getResource` endpoints that answer queries of the form "does this
resource exist" or "provide this resource if it exists". Rationale: for
such endpoints, absence is an expected condition and thus aligns better
with the "successful" connotation of 2xx error codes than with the
"unsuccessful" connotation of 4xx codes. For example, browsers and Web
servers typically log 404 codes as errors.

**Transport protocol: HTTPS**

Default to HTTPS, even for testing. Never circumnavigate host name
validation or other HTTPS mechanisms.

**Auth-n/z: Use OAuth2 Bearer tokens**

Endpoints that require authentication/authorization should observe the
"Authorization" HTTP header parameter. The canonical auth scheme is
OAuth2 "Bearer", i.e., authorization headers take the form
`Authorization: Bearer <token>`.

Using [Auth Tokens](https://github.com/palantir/auth-tokens) is recommended.

**Auth-n/z: handled by auth backend**

For authorization, backend services typically do not introspect or
validate tokens and instead proxy auth concerns to auth services.
When introspecting auth tokens (e.g., to extract a user name),
extracted fields must be validated using the proper mechanisms of
the auth framework used; for example, JWT token integrity must be
validated by verifying the signature.

## Java Server Implementation

**State: handled by backing store**

Wherever possible, services should be stateless in order to facilitate
scaling, failover, and recovery. Application state is stored in an
appropriate backing store, where necessary with transactional
guarantees.

**Service annotations: use JAX-RS annotations**

The `javax.ws.rs` JAX-RS implementation is the canonical choice since it
is understood by both server (e.g., Dropwizard) and client (e.g., Feign,
Retrofit) implementations.

**JSON wire-format: encode with Jackson**

[Jackson](https://github.com/FasterXML/jackson) is the canonical choice
for encoding/decoding Java value types. Where possible, specify the
serialization/deserialization mechanism via `jackson-databind`
annotations rather than explicit Jackson modules in order to obviate the
need for a Jackson core dependency and module registration.

**Complex value types: use annotation processor**

[Value types](https://en.wikipedia.org/wiki/Value_type) should be
implemented with an annotation processor (e.g.,
[FreeBuilder](https://github.com/google/FreeBuilder) or
[Immutables](https://immutables.github.io/intro.html)) rather than as
custom beans with handwritten (or even IDE-generated) `equals()`
implementations.

**Error handling: throw and JSON-encode exceptions**

Most JAX-RS-compatible servers support a pluggable exception mapping
mechanism that allows server code to throw exceptions and map them to an
HTTP error code. The exception mapper should also attach the serialized
exception (optionally including the stack trace) to the response. *Never
rely on Java serialization for exceptions since it is brittle across
Java versions*, instead use a JSON exception mapper.

Common exception types in `javax.ws.rs` interfaces are
`NotFoundException` (maps to 204 or 404) and `ForbiddenException` (maps
to 403).

**Note**: Be wary of leaking sensitive information via serialized stack traces
that surface in client UIs or logs.

**Indicating absence**

According to the section on [error handling](#Error-handling), services should indicate
absence by responding with a 204 or 404 error code (see
Error handling: throw and [JSON-encode exceptions](#error-handling-throw-and-json-encode-exceptions)
above). For endpoints that answer queries of the form "does this resource exist" or "provide
this resource if it exists", we recommend indicating absence by error
code 204 and specifying the Java method signature by a Java8
`Optional` type in order to facilitate exception-free client code (see
[digesting absence](#Digesting-absence)), for example:

```java
@GET
@Path("/resources/{resourceName}")
@Produces(MediaType.APPLICATION_JSON)
Optional<Resource> getResource(@PathParam("resourceName") String resourceName);
```

**Note**: This method never returns an empty JSON object, or a
Jackson-serialized version of `Optional#empty()`. At the HTTP-level,
it returns a 204 response that clients can transparently decode into
`Optional#empty()`, see Digesting absence below.

**HTTP Compression: used**

Enable GZIP compression for APIs where it makes sense. Note that
Dropwizard requires special configuration in order to support
compression on POST requests.

## Java Client Implementation

**Client library: Feign and okhttp**

The combination of [Feign](https://github.com/Netflix/feign) and
[okhttp](https://github.com/square/okhttp) is currently the recommended
library to build Java proxies from JAX-RS interfaces.

Use [http-remoting](https://github.com/palantir/http-remoting) to help adhere
to HTTP remoting standards described in this document.

**JSON wire-format: decode with Jackson**

Analogous to server-side Jackson JSON serialization, use Jackson (i.e.,
in conjunction with
[feign-jackson](https://github.com/Netflix/feign/tree/master/jackson) to
deserialize JSON into Java objects.

**Error handling: decode HTTP codes to Java exceptions**

Symmetric to the case of server error encoding (see
Error handling: throw and [JSON-encode exceptions](#error-handling-throw-and-json-encode-exceptions))),
clients should decode common HTTP error codes by throwing appropriate exceptions, e.g.
`NotFoundException` for 204 or 404 codes, or `ForbiddenException` for 403.
See [Digesting absence](#Digesting-absence) for recommendations on handling 204 codes
with Optionals.

**Error handling: try to decode exceptions, provide fallback**

Provide an exception deserializer corresponding to the server's
exception serializer (see Error handling: throw and
[JSON-encode exceptions](#error-handling-throw-and-json-encode-exceptions))
and re-throw the original error if possible, or a RuntimeException otherwise.

**Digesting absence**

As discussed above (see [Error handling](#error-handling) and
[Indicating absence](#indicating-absence)), the service will respond with error 204 in
case the requested resource is absent. If the Java interface has
return type `Optional<Foo>`, then the client should decode 204
responses to `Optional#empty`.

## Java Project Layout

**API and implementation: in separate projects**

The project layout described in this section has two main purposes:

- it reduces the number of dependencies exposed by the public JAX-RS
  API library

- it allows for independent versioning of the API and its
  implementation, thus enabling long-lived, stable APIs with
  frequently changing implementations

**External API: in a (mostly) dependency-free API module**

Per service, a `<service>-api` module exposes the set of JAX-RS
interfaces and data objects (see [here](#complex-value-types-use-annotation-processor))
see that define the API access to the service; ideally the interfaces
are JAX-RS annotated, and we should strongly prefer
Immutables/FreeBuilder-generated data objects over hand-written beans.

Exported dependencies may include, at most, JAX-RS annotations,
jackson-databind and Guava (restricted to use only Guava Optionals and
Preconditions checks for data object validation, and expected to work
with Guava versions 14 or higher). Depending on the requirements for
clients consuming this API, consider making it Java7-compatible.

**Service implementation: in a separate module**

The server implementation of the above API resides in a separate module
`<service>-service`. Implementors have free reign over dependencies and
Java version requirements. This package should be designed such that no
API user has to (or would even want to) import any of its functionality.

## Frontend-Serving Services

This section discusses topics specific to services that serve content to
frontend applications.

**CORS-compatibility: support preflight requests**

Frontend-serving services should handle unauthenticated preflight
OPTIONS requests in order to support
[CORS](http://www.html5rocks.com/en/tutorials/cors/).

**Response time: at most 100ms for synchronous requests**

Response times for frequent read requests should be on the order of
50-100ms in order to yield responsive UIs. Immutable data can be cached
without introducing server state.

## Deployment

**Unified public API: use proxy**

Use a reverse proxy (e.g., nginx, Apache httpd) to stitch together
services as necessary when constructing a unified public API. This also
simplifies cert management in some deployment scenarios.

**Provide Docker container**

In order to facilitate integration testing with other services, publish
a separate Docker container for every service.

**HTTPS/SSL: Provide CA-signed certificate for testing**

Every service should by default expose a CA-signed certificate
for a non-public host name such as `<service>.dev`. Deployment and
Docker distributions should ship this certificate. Self-signed
certificates should be avoided except for local testing against
`localhost`.

Rationale: A CA-based trust chain simplifies service composition since
every service's trust store has to trust only the CA rather than every
individual self-signed certificate.
