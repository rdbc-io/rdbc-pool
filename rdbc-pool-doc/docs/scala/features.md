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
    
The project provides a thread-safe, non-blocking connection pool implementation
currently providing a basic feature set. Apart from maintaining a connection pool
it provides the following features:

1.  **Connection validation.**

    Connections a connection is returned to client it is validated using
    `Connection#validate` method.

2.  **Rollback on return.**

    On every connection returned to the pool a rollback operation is performed
    to ensure that clients don't get connections with previously started
    and abandoned transactions.

3.  **Queueing connection requests.**

    Connection requests don't block the executing thread (as per `ConnectionFactory` contract).
    Instead, a connection request queue is maintained.
    
For planned features see GitHub issues labeled with `feature` label
[here](https://github.com/rdbc-io/rdbc-pool/issues?q=is%3Aissue+is%3Aopen+label%3Afeature).
