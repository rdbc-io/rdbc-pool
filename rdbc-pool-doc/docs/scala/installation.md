<!---
 ! Copyright 2016-2017 rdbc contributors
 !
 ! Licensed under the Apache License, Version 2.0 (the "License");
 ! you may not use this file except in compliance with the License.
 ! You may obtain a copy of the License at
 !
 !     http://www.apache.org/licenses/LICENSE-2.0
 !
 ! Unless required by applicable law or agreed to in writing, software
 ! distributed under the License is distributed on an "AS IS" BASIS,
 ! WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ! See the License for the specific language governing permissions and
 ! limitations under the License. 
 -->

## Adding rdbc-pool to your project

rdbc and rdbc-pool JARs are published to
[Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.rdbc.pool%22)
repository. The library is currently available for Scala 2.11 and 2.12 and requires
Java 8 runtime or later. rdbc-pool targets {{rdbc_version}} rdbc API version.

### SBT
For sbt projects, add the following to `build.sbt`:
```scala
libraryDependencies ++= Vector(
  "io.rdbc" %% "rdbc-api-scala" % "{{rdbc_version}}",
  "io.rdbc.pool" %% "rdbc-pool-scala" % "{{version}}"
)
```

### Gradle
For Gradle projects, add the following to the `dependencies` section of `build.gradle`:

Scala 2.12
```groovy
compile group: 'io.rdbc', name: 'rdbc-api-scala_2.12', version: '{{rdbc_version}}'
compile group: 'io.rdbc.pool', name: 'rdbc-pool-scala_2.12', version: '{{version}}'
```

Scala 2.11
```groovy
compile group: 'io.rdbc', name: 'rdbc-api-scala_2.11', version: '{{rdbc_version}}'
compile group: 'io.rdbc.pool', name: 'rdbc-pool-scala_2.11', version: '{{version}}'
```

### Maven
For Maven projects, add the following to the `dependencies` element of `pom.xml`:

Scala 2.12
```xml
<dependency>
  <groupId>io.rdbc</groupId>
  <artifactId>rdbc-api-scala_2.12</artifactId>
  <version>{{rdbc_version}}</version>
</dependency>
<dependency>
  <groupId>io.rdbc.pool</groupId>
  <artifactId>rdbc-pool-scala_2.12</artifactId>
  <version>{{version}}</version>
</dependency>
```

Scala 2.11
```xml
<dependency>
  <groupId>io.rdbc</groupId>
  <artifactId>rdbc-api-scala_2.11</artifactId>
  <version>{{rdbc_version}}</version>
</dependency>
<dependency>
  <groupId>io.rdbc.pool</groupId>
  <artifactId>rdbc-pool-scala_2.11</artifactId>
  <version>{{version}}</version>
</dependency>
```
