# Performance and resilience profile

## Reproduction

The profile creates a fresh Datomic Local database, generates deterministic payment data, exercises the Ring API in-process, and deletes the database afterwards. It measures application/Datomic work only; it is not an internet or Stripe benchmark.

```powershell
docker run --rm -e PERF_SAMPLE_SIZE=30 -e PERF_WEBHOOK_COUNT=100 -e PERF_PROVIDER_LATENCY_MS=100 -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:perf
```

Use `PERF_PROVIDER_LATENCY_MS=5000` to run the required five-second provider-latency resilience scenario. The Fake Provider supports this without contacting Stripe.

## Recorded run

Executed on 2026-08-31 in `clojure:temurin-21-tools-deps` (Java 21.0.12), Docker Desktop, 4 available processors, heap maximum 1,556,086,784 bytes. Dataset: 30 payments; webhook burst: 100 synthetic signed-format events; sequential execution. Values below include the first request and are therefore descriptive, not SLOs.

| Workload | Throughput/s | p50 | p95 | p99 | Error rate |
| --- | ---: | ---: | ---: | ---: | ---: |
| GET payment | 1887.1 | 0.46 ms | 1.07 ms | 1.33 ms | 0% |
| GET history | 112.0 | 2.20 ms | 4.01 ms | 202.47 ms | 0% |
| POST payment / Fake Provider | 364.8 | 2.32 ms | 4.04 ms | 10.43 ms | 0% |
| Webhook enqueue burst | 2380.6 | 0.38 ms | 0.52 ms | 0.67 ms | 0% |
| Fake Provider latency (100 ms configured) | 9.78 | 102.25 ms | 102.25 ms | 102.25 ms | 0% |

Runtime snapshot: 42 GC collections, 205 ms cumulative GC time, 40,147,728 bytes heap used at collection. Datomic transaction latency is represented by the write profile because each POST persists the payment and provider result.

## Resilience limits

- A five-second provider delay is simulated by `:slow-success`; the payment call blocks and no fallback provider charge is attempted.
- Provider timeout/unknown-outcome and reconciliation are covered by the existing integration suite.
- Kafka publication failure leaves the relay checkpoint unchanged; restart republishes safely. This is covered by `event-relay-integration-test`.
- A webhook burst is idempotently enqueued; duplicate-event handling and recovery are covered by webhook unit/integration tests.
- Datomic temporary-failure behavior is isolated from destructive reconciliation transitions; deferred reconciliation is covered by integration tests.

## Observation / decision

Observation: history has a cold-query outlier near 202 ms while steady reads and writes are low milliseconds. Hypothesis: Datomic history query initialization dominates the first access. Experiment: 30 sequential calls against a fresh database. Decision: no cache or query rewrite was added; this profile is intentionally small and reproducible. Re-run with a larger dataset before adopting production SLOs.
