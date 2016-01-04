# rexster-explorer
###### A web application to browse and visualize graphs exposed by Rexster's REST API

rexster-explorer is a ClojureScript application for the browser that allows to visualize
graphs exposed by [Rexster](https://github.com/tinkerpop/rexster/wiki).

In addition to Rexster's server a little HTTP proxy, written in Clojure, is needed to
provide the browser with access to arbitrary URLs.

To start the application run

```
$ lein uberjar
$ java -jar target/rexster-explorer-0.1.0-SNAPSHOT-standalone.jar
```

in the working copy of this repository. A web server should start listening
on port 8080 which will serve both the HTTP proxy and static content.
