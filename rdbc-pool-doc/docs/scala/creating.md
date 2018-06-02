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

A connection pool is a specialized `ConnectionFactory` implementation that
maintains a set of ready to use connections and performs connection validation
before returning them to clients. The pool delegates connection requests to 
underlying factory provided by client during pool creation.

## Creating a pool

The pool is implemented in `ConnectionPool` class.  To create instance of
the pool use `ConnectionPool` companion object's `apply` method as follows:

```
#!scala
import io.rdbc.pool.sapi.ConnectionPool
import io.rdbc.pool.sapi.ConnectionPoolConfig

val cf: ConnectionFactory = ???

val pool = ConnectionPool(cf, ConnectionPoolConfig())

```

To create the pool you first need to create and configure a connection factory
provided by rdbc driver you use (see the line 4 in the above snippet) and then
pass it as the `ConnectionPool#apply` argument (line 6). The second argument
of the `apply` method is `ConnectionPoolConfig` instance holding the pool
configuration. See [configuration](configuration.md) chapter for available
configuration options.

## Closing a pool

As any other `ConnectionFactory`, a pool needs to be shut down to clean up
resources. Use `ConnectionPool#shutdown` method to do it.
