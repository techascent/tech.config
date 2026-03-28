# tech.config

A Clojure, ClojureScript, and Babashka configuration library that abstracts
configuration from files and environment variables.

The library works by reading config files named `*-config.edn` from the resources
directory (classpath in Clojure, a configurable directory in ClojureScript/Node).
This defines a number of config variables and values. An example is as follows:

### Config File (e.g. `app-config.edn`):

    {
       :my-setting "value"
    }

Each of the settings can be any type, the final type of the value will be
decided by base of the precedence hierarchy defined below.

### Usage

Add the dependency to your `deps.edn`:

```clojure
techascent/tech.config {:mvn/version "0.4.0"}
```

with the private maven repo:

```clojure
:mvn/repos {"releases" {:url "s3://techascent.jars/releases/"}}
```

#### Getting config values

```clojure
(require '[tech.config.core :refer [get-config]])

(get-config :my-setting)
```

#### Overwriting config values (Clojure only)

In the event that you wish to programatically overwrite a config setting, it is
possible to use the `with-config` macro as follows:

```clojure
(require '[tech.config.core :refer [get-config with-config]])

(with-config [:my-setting true]
 (get-config :my-setting))       ; => true
```

### ClojureScript (Node.js)

The ClojureScript version reads config from:

1. EDN files in a configurable directory (defaults to `resources/`, override with
   `CONFIG_DIR` env var)
2. Environment variables (which take precedence over files)

```clojure
(ns my-app.core
  (:require [tech.config.core :as config]))

(config/get-config :my-setting)
(config/get-config :my-setting "default-value")
(config/print-config)
```

### Babashka

The Babashka version reads config from classpath directories (configured via
`:paths` in `bb.edn`) and supports `CONFIG_DIR` for scripts that run without a
`bb.edn`.

```clojure
#!/usr/bin/env bb
(require '[tech.config.core :as config])

(config/get-config :my-setting)
(config/get-config :my-setting "default-value")
```

### Precedence Hierarchy

#### Clojure

`with-config` > `environment` > `user-config.edn` > `app-config.edn` > `libraries (a-z)`

Any `*-config.edn` found within the application or a dependency will be merged
into the config. The order that this occurs is reverse alphabetical with
`app-config.edn` and `user-config.edn` moved to the top of the stack
respectively.

Values set through the environment will take precedence over those specified in
`*-config.edn` files.

#### ClojureScript

`environment` > EDN files (sorted alphabetically in config directory)

### Types

One major advantage over other configuration options is types. The bottom of the
configuration stack defines the type (i.e. when a library specifies a default
value, it also specifies the type because the `.edn` files are typed). Any
configuration layer that overwrites this value gets coerced to the type specified
at the base. As a consequence, things specified through the environment which
come in as strings will be converted to the appropriate type and the application
can read these types without performing the conversion on its own.

### Sources Map

The sources map (obtained by calling `(get-config-table-str)`) provides a table
like the one shown below. This is convenient to show at start up so that it is
possible to see where configuration options are being set and what the types are
(e.g. strings are shown in "quotes"). If something is set by the environment the
source will be listed as `environment`.

```
Key                    Value            Source
-------------------------------------------------------
:app-config-overwrite  1                app-config.edn
:boolean               true             test-config.edn
:env-config-overwrite  false            environment
:number                42               test-config.edn
:os-arch               "amd64"          zzz-config.edn
:os-name               "Linux"          zzz-config.edn
:os-version            "4.8.0-26-generic" zzz-config.edn
:overwrite             30               test-config.edn
:string                "hello world"    with-config
:user-config-overwrite 2                user-config.edn
```

### Development

#### Running Clojure tests

```bash
clojure -X:test
```

#### Running ClojureScript tests

```bash
npm install
npx shadow-cljs compile test
node target/test.js
```

#### Running Babashka tests

```bash
bb test/bb_test_runner.clj
```

#### Running all tests

```bash
scripts/test
```

#### Releasing

```bash
scripts/release.sh
```

Requires Vault access for AWS credentials (uses the `core` role for S3 maven
repo write access).
