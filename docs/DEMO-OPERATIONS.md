# Demo operations boundaries

The live Gemini quota is a global, in-memory quota for one backend process. The demo compose setup is intended to run one backend instance. A multi-instance deployment must replace this limiter with a shared store before it is enabled.

The scheduled demo reset truncates only the simulated operations, diagnosis, retry, and audit tables, then reseeds the known two-task dataset in one PostgreSQL transaction. Operator credentials and the curated knowledge index are intentionally preserved.

The current diagnostic workflow reserves one quota unit only after the target is valid, no active diagnosis exists, and the path is model-backed. Its version-incompatibility path performs one model interaction; the callback-timeout path is deterministic and does not reserve quota. If the workflow grows additional model rounds, the quota must move to a reservation at the model-call boundary before that change is released.
