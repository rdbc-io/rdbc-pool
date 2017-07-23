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
!!! warning
    rdbc-pool project and this documentation is still a work in progress.
    It's not ready yet for production use.

The pool configuration is represented by [`ConnectionPoolConfig`]()
instance. Instances of this class can be created using its companion object's
`apply` method. Every parameter of this method has some default value
so you can use named parameters to set only some configuration options and
keep others at default values.

## Options

The list below contains available configuration options.

-    **name**

     Name of the pool. Appears in logs.
     
     Default value: `:::scala "unnamed"`.

---

-    **size**

     Size of the pool. The pool will maintain at most `size` connections
     at any given time, this includes both idle and in-use connections. If some
     connection is closed the pool will make its best to replace it as soon as
     possible so that the pool always contains `size` connections. 
     
     Default value: `:::scala 20`.

---

-    **validateTimeout**

     If the connection validity can't be determined in this time, the connection
     is deemed invalid and is closed and eventually removed from the pool.
     
     Default value: `:::scala Timeout(5.seconds)`.

---

-    **connectTimeout**

     If opening the connection doesn't finish in this time, the attempt is aborted.
     Note that this property doesn't control the maximum time which clients will wait
     for connections, it's only for pool's internal connection requests. 
          
     Default value: `:::scala Timeout(5.seconds)`.

---

-    **rollbackTimeout**

     When clients return connections to the pool, the pool rolls back any transaction
     that may be in progress so that other clients are unaffected by the connection
     usage. This property controls maximum time that the rollback operation can take.
     If it takes longer the connection is closed and removed from the pool. 
          
     Default value: `:::scala Timeout(5.seconds)`.

---

-    **taskScheduler**

     When clients return connections to the pool, the pool rolls back any transaction
     that may be in progress so that other clients are unaffected by the connection
     usage. This property controls maximum time that the rollback operation can take.
     If it takes longer the connection is closed and removed from the pool. 
          
     Default value:
     
     A JDK scheduler using single thread and using global execution
     context for internal executions:
     
        #!scala     
        () => new JdkScheduler(
                Executors.newSingleThreadScheduledExecutor()
              )(ExecutionContext.global)     

---

-    **ec**

     Execution context that will be used by the pool internally to execute
     callbacks on `Future`s.
          
     Default value: `:::scala ExecutionContext.global`.


## Examples

The snippet below creates a pool specifying its name, size and validation timeout.

```scala
import scala.concurrent.duration._

import io.rdbc.sapi._
import io.rdbc.pool.sapi._

val cf: ConnectionFactory = ???

val pool = ConnectionPool(cf, ConnectionPoolConfig(
              name = "mypool",
              size = 50,
              validateTimeout = 5.seconds.timeout)
           )
```
