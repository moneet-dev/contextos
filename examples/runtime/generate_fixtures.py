"""Generates the deterministic runtime fixture in examples/runtime (INC-143..INC-146).

Usage: python generate_fixtures.py [output-dir]   (default: this script's directory)
"""
import json
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
(out / "telemetry").mkdir(parents=True, exist_ok=True)
rnd = random.Random(143)

T0 = datetime(2026, 9, 30, 13, 0, tzinfo=timezone.utc)
START = datetime(2026, 9, 30, 14, 2, tzinfo=timezone.utc)


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%S") + (".%03dZ" % (t.microsecond // 1000))


def at(h, m, s=0, ms=0):
    return datetime(2026, 9, 30, h, m, s, ms * 1000, tzinfo=timezone.utc)


# ---------------------------------------------------------------- topology
services = {
    "services": [
        {"name": "api-gateway", "kind": "SERVICE", "description": "Public HTTPS entry point"},
        {"name": "checkout-service", "kind": "SERVICE", "description": "Cart checkout orchestration"},
        {"name": "refund-batch", "kind": "SERVICE", "description": "Scheduled bulk refund job"},
        {"name": "payment-service", "kind": "SERVICE", "description": "Charges and refunds",
         "repository": "payment-service"},
        {"name": "fraud-api", "kind": "EXTERNAL", "description": "Third-party fraud scoring"},
        {"name": "payments-db", "kind": "DATABASE", "description": "PostgreSQL, payments schema"},
        {"name": "payment-events", "kind": "QUEUE", "description": "Kafka topic payment-events"},
        {"name": "notification-service", "kind": "SERVICE", "description": "Customer emails"},
    ],
    "dependencies": [
        {"from": "api-gateway", "to": "checkout-service", "kind": "CALLS"},
        {"from": "checkout-service", "to": "payment-service", "kind": "CALLS"},
        {"from": "refund-batch", "to": "payment-service", "kind": "CALLS"},
        {"from": "payment-service", "to": "fraud-api", "kind": "CALLS"},
        {"from": "payment-service", "to": "payments-db", "kind": "QUERIES"},
        {"from": "payment-service", "to": "payment-events", "kind": "PUBLISHES"},
        {"from": "notification-service", "to": "payment-events", "kind": "CONSUMES"},
    ],
}
(out / "services.json").write_text(json.dumps(services, indent=2) + "\n")

incidents = {
    "incidents": [{
        "id": "INC-143",
        "title": "payment-service 5xx elevated",
        "severity": "SEV2",
        "startedAt": iso(START),
        "detectedAt": iso(at(14, 6)),
        "affectedServices": ["payment-service"],
        "symptoms": [
            "HTTP 5xx on payment-service up 320%",
            "Checkout failures reported by customers",
        ],
    }]
}

# ---------------------------------------------------------------- changes
changes = [
    {"timestamp": "2026-09-29T16:20:00.000Z", "service": "payment-service", "type": "DEPLOY",
     "description": "Deployed v2.14.0: refunds now load transaction history "
                    "(PaymentTransactionRepository.findByPaymentId)"},
    {"timestamp": "2026-09-30T11:00:00.000Z", "service": "notification-service", "type": "CONFIG",
     "description": "Rotated SMTP credentials"},
    {"timestamp": iso(at(13, 58)), "service": "refund-batch", "type": "JOB",
     "description": "Refund batch REF-0930 started: 48213 payments queued"},
]

# ---------------------------------------------------------------- metrics


def series(base, noise, onset=None, level=None, ramp=1):
    def value(t):
        v = base + rnd.uniform(-noise, noise)
        if onset is not None and t >= onset:
            k = min(1.0, ((t - onset).total_seconds() / 60 + 1) / ramp)
            v = base + (level - base) * k + rnd.uniform(-noise, noise) * 3
        return max(0.0, v)
    return value


metrics = [
    ("payment-service", "http_5xx_per_min", series(25, 3, at(14, 2), 105, ramp=3)),
    ("payment-service", "http_p99_latency_ms", series(240, 20, at(14, 1), 29800, ramp=2)),
    ("payment-service", "refund_requests_per_min", series(2, 1, at(13, 58), 1800)),
    ("payments-db", "active_connections", series(12, 2, at(13, 59), 50)),
    ("payments-db", "query_p99_ms", series(9, 2, at(13, 59), 8500, ramp=2)),
    ("fraud-api", "p99_latency_ms", series(120, 10)),
    ("checkout-service", "http_5xx_per_min", series(4, 1, at(14, 3), 60, ramp=3)),
    ("notification-service", "email_failures_per_min", series(1, 0.5, at(14, 20), 15)),
]
metric_rows = []
for minute in range(0, 91):
    t = T0 + timedelta(minutes=minute)
    for service, metric, fn in metrics:
        v = fn(t)
        if metric == "active_connections":
            v = min(50.0, round(v))
        metric_rows.append(f"{iso(t)},{service},{metric},{v:.1f}")

# ---------------------------------------------------------------- logs
logs = [
    (at(13, 30, 12), "fraud-api", "WARN", "fraud.RateLimiter",
     "Rate limit at 80% for client payment-service", None, None),
]
for i in range(40):
    t = at(13, 59, 5) + timedelta(seconds=i * 27 + rnd.randint(0, 9))
    logs.append((t, "payments-db", "WARN", "postgres",
                 f"duration: {rnd.randint(7800, 9400)} ms  statement: SELECT * FROM payment_transactions "
                 f"WHERE payment_id = $1 ORDER BY created_at", None, None))
for i in range(12):
    t = at(14, 0, 40) + timedelta(seconds=i * 90 + rnd.randint(0, 20))
    logs.append((t, "payment-service", "WARN", "com.zaxxer.hikari.pool.HikariPool",
                 f"HikariPool-1 - Pool stats (total=50, active=50, idle=0, waiting={rnd.randint(120, 260)})",
                 None, None))
for i in range(60):
    t = at(14, 2, 3) + timedelta(seconds=i * 18 + rnd.randint(0, 7))
    logs.append((t, "payment-service", "ERROR", "com.example.payments.service.PaymentService",
                 f"Charge failed for customer {rnd.randint(1000, 9999)}",
                 "org.springframework.transaction.CannotCreateTransactionException: Could not open JPA "
                 "EntityManager for transaction; nested exception is java.sql.SQLTransientConnectionException: "
                 "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
                 "c%04d" % i))
for i in range(30):
    t = at(14, 3, 10) + timedelta(seconds=i * 35 + rnd.randint(0, 9))
    logs.append((t, "checkout-service", "ERROR", "checkout.PaymentClient",
                 "Payment call failed: 502 Bad Gateway from payment-service", None, "c%04d" % i))
for i in range(10):
    t = at(14, 20, 15) + timedelta(seconds=i * 50)
    logs.append((t, "notification-service", "ERROR", "notify.SmtpSender",
                 "SMTP send failed: connection timed out (smtp.mailprovider.example)", None, None))


# ---------------------------------------------------------------- traces
spans = []
span_seq = [0]


def span(trace, parent, service, operation, start, duration, status="OK", peer=None, error=None):
    span_seq[0] += 1
    s = {"traceId": trace, "spanId": "s%05d" % span_seq[0], "service": service, "operation": operation,
         "start": iso(start), "durationMs": duration, "status": status}
    if parent:
        s["parentSpanId"] = parent
    if peer:
        s["peer"] = peer
    if error:
        s["error"] = error
    spans.append(s)
    return s["spanId"]


ERR = "CannotCreateTransactionException"

# Baseline (13:02-13:46): normal charges and refunds
for i in range(24):
    t = at(13, 2) + timedelta(minutes=i * 1.8, seconds=rnd.randint(0, 30))
    tr = "b%04d" % i
    if i % 3 == 0:
        root = span(tr, None, "payment-service", "POST /payments/{id}/refund", t, rnd.randint(30, 55))
        span(tr, root, "payment-service", "SELECT payment_transactions", t, rnd.randint(8, 16), peer="payments-db")
        span(tr, root, "payment-service", "UPDATE payments", t, rnd.randint(3, 7), peer="payments-db")
    else:
        co = span(tr, None, "checkout-service", "POST /checkout", t, rnd.randint(220, 320))
        root = span(tr, co, "payment-service", "POST /payments", t, rnd.randint(160, 230), peer=None)
        span(tr, root, "payment-service", "POST /check", t, rnd.randint(95, 130), peer="fraud-api")
        span(tr, root, "payment-service", "INSERT payments", t, rnd.randint(3, 8), peer="payments-db")

# Incident window: slow refunds hold every connection, charges time out
for i in range(30):
    t = at(13, 58, 30) + timedelta(seconds=i * 50 + rnd.randint(0, 10))
    tr = "r%04d" % i
    db = rnd.randint(7800, 9400)
    root = span(tr, None, "payment-service", "POST /payments/{id}/refund", t, db + rnd.randint(20, 60))
    span(tr, root, "payment-service", "SELECT payment_transactions", t, db, peer="payments-db")
    span(tr, root, "payment-service", "UPDATE payments", t, rnd.randint(3, 9), peer="payments-db")

for i in range(40):
    t = at(14, 2, 3) + timedelta(seconds=i * 27 + rnd.randint(0, 9))
    tr = "c%04d" % i
    failed = i % 10 != 9
    co = span(tr, None, "checkout-service", "POST /checkout", t,
              30050 if failed else rnd.randint(240, 330),
              "ERROR" if failed else "OK", error="502 Bad Gateway" if failed else None)
    root = span(tr, co, "payment-service", "POST /payments", t,
                30000 if failed else rnd.randint(170, 240),
                "ERROR" if failed else "OK", error=ERR if failed else None)
    span(tr, root, "payment-service", "POST /check", t, rnd.randint(95, 135), peer="fraud-api")
    if not failed:
        span(tr, root, "payment-service", "INSERT payments", t, rnd.randint(4, 9), peer="payments-db")

# ================================================================ more incidents
# INC-144..146 follow INC-143 in every file. Each uses its own random generator,
# so INC-143's records are exactly what they were before these were added.

CHARGE_POOL_ERROR = ("org.springframework.transaction.CannotCreateTransactionException: Could not open JPA "
                     "EntityManager for transaction; nested exception is java.sql.SQLTransientConnectionException: "
                     "HikariPool-1 - Connection is not available, request timed out after 30000ms.")


def noisy(rng, base, noise, onset=None, level=None, ramp=1):
    def value(t):
        v = base + rng.uniform(-noise, noise)
        if onset is not None and t >= onset:
            k = min(1.0, ((t - onset).total_seconds() / 60 + 1) / ramp)
            v = base + (level - base) * k + rng.uniform(-noise, noise) * 3
        return max(0.0, v)
    return value


def add_metrics(rng, start, overrides):
    """The eight series INC-143 uses, at their baselines unless overridden, one sample a minute."""
    series_by_key = {
        ("payment-service", "http_5xx_per_min"): noisy(rng, 25, 3),
        ("payment-service", "http_p99_latency_ms"): noisy(rng, 240, 20),
        ("payment-service", "refund_requests_per_min"): noisy(rng, 2, 1),
        ("payments-db", "active_connections"): noisy(rng, 12, 2),
        ("payments-db", "query_p99_ms"): noisy(rng, 9, 2),
        ("fraud-api", "p99_latency_ms"): noisy(rng, 120, 10),
        ("checkout-service", "http_5xx_per_min"): noisy(rng, 4, 1),
        ("notification-service", "email_failures_per_min"): noisy(rng, 1, 0.5),
    }
    series_by_key.update(overrides)
    t0 = start - timedelta(minutes=62)
    for minute in range(0, 91):
        t = t0 + timedelta(minutes=minute)
        for (service, metric), fn in series_by_key.items():
            v = fn(t)
            if metric == "active_connections":
                v = min(50.0, round(v))
            metric_rows.append(f"{iso(t)},{service},{metric},{v:.1f}")


def add_baseline_traces(rng, prefix, start):
    """Normal charges and refunds in the baseline period, as for INC-143."""
    for i in range(24):
        t = start - timedelta(minutes=60) + timedelta(minutes=i * 1.8, seconds=rng.randint(0, 30))
        tr = f"{prefix}-b{i:04d}"
        if i % 3 == 0:
            root = span(tr, None, "payment-service", "POST /payments/{id}/refund", t, rng.randint(30, 55))
            span(tr, root, "payment-service", "SELECT payment_transactions", t, rng.randint(8, 16), peer="payments-db")
            span(tr, root, "payment-service", "UPDATE payments", t, rng.randint(3, 7), peer="payments-db")
        else:
            co = span(tr, None, "checkout-service", "POST /checkout", t, rng.randint(220, 320))
            root = span(tr, co, "payment-service", "POST /payments", t, rng.randint(160, 230))
            span(tr, root, "payment-service", "POST /check", t, rng.randint(95, 130), peer="fraud-api")
            span(tr, root, "payment-service", "INSERT payments", t, rng.randint(3, 8), peer="payments-db")


def checkout_errors(rng, first, count, trace_prefix):
    for i in range(count):
        t = first + timedelta(seconds=i * 40 + rng.randint(0, 9))
        logs.append((t, "checkout-service", "ERROR", "checkout.PaymentClient",
                     "Payment call failed: 502 Bad Gateway from payment-service", None, f"{trace_prefix}{i:04d}"))


# ---------------------------------------------------------------- INC-144: bad deploy
rng = random.Random(144)
S = datetime(2026, 10, 7, 10, 20, tzinfo=timezone.utc)
incidents["incidents"].append({
    "id": "INC-144", "title": "payment-service 5xx on charges", "severity": "SEV2",
    "startedAt": iso(S), "detectedAt": iso(S + timedelta(minutes=4)),
    "affectedServices": ["payment-service"],
    "symptoms": ["HTTP 5xx on POST /payments", "Checkout failures reported by customers"],
})
changes.append({"timestamp": iso(S - timedelta(hours=5)), "service": "notification-service", "type": "CONFIG",
                "description": "Updated receipt email templates"})
changes.append({"timestamp": iso(S - timedelta(minutes=3)), "service": "payment-service", "type": "DEPLOY",
                "description": "Deployed v2.16.0: PaymentService.charge now builds the receipt "
                               "from the saved PaymentTransaction"})
add_metrics(rng, S, {
    ("payment-service", "http_5xx_per_min"): noisy(rng, 25, 3, S - timedelta(minutes=2), 140, ramp=2),
    ("checkout-service", "http_5xx_per_min"): noisy(rng, 4, 1, S - timedelta(minutes=1), 70, ramp=2),
})
for i in range(3):
    logs.append((S - timedelta(minutes=8) + timedelta(minutes=i * 9), "fraud-api", "WARN", "fraud.RateLimiter",
                 "Rate limit at 80% for client payment-service", None, None))
for i in range(50):
    t = S - timedelta(minutes=2, seconds=20) + timedelta(seconds=i * 20 + rng.randint(0, 6))
    logs.append((t, "payment-service", "ERROR", "com.example.payments.service.PaymentService",
                 f"Charge failed for customer {rng.randint(1000, 9999)}",
                 'java.lang.NullPointerException: Cannot invoke "com.example.payments.domain.Payment.getId()" '
                 'because "payment" is null', f"d144-{i:04d}"))
checkout_errors(rng, S - timedelta(minutes=1), 20, "d144-")
add_baseline_traces(rng, "d144", S)
for i in range(40):
    t = S - timedelta(minutes=2, seconds=10) + timedelta(seconds=i * 25 + rng.randint(0, 9))
    tr = f"d144-{i:04d}"
    failed = i % 7 != 6
    co = span(tr, None, "checkout-service", "POST /checkout", t, rng.randint(60, 90) if failed else rng.randint(240, 330),
              "ERROR" if failed else "OK", error="502 Bad Gateway" if failed else None)
    root = span(tr, co, "payment-service", "POST /payments", t, rng.randint(35, 55) if failed else rng.randint(170, 240),
                "ERROR" if failed else "OK", error="NullPointerException" if failed else None)
    span(tr, root, "payment-service", "POST /check", t, rng.randint(95, 130), peer="fraud-api")
    span(tr, root, "payment-service", "INSERT payments", t, rng.randint(3, 8), peer="payments-db")

# ---------------------------------------------------------------- INC-145: slow upstream
rng = random.Random(145)
S = datetime(2026, 10, 14, 16, 40, tzinfo=timezone.utc)
incidents["incidents"].append({
    "id": "INC-145", "title": "payment-service latency and 5xx", "severity": "SEV2",
    "startedAt": iso(S), "detectedAt": iso(S + timedelta(minutes=5)),
    "affectedServices": ["payment-service"],
    "symptoms": ["POST /payments p99 latency above 10 seconds", "HTTP 5xx on payment-service"],
})
changes.append({"timestamp": iso(S - timedelta(hours=6)), "service": "checkout-service", "type": "DEPLOY",
                "description": "Deployed checkout-service v5.2.1: cart page copy changes"})
add_metrics(rng, S, {
    ("fraud-api", "p99_latency_ms"): noisy(rng, 120, 10, S - timedelta(minutes=3), 9000, ramp=2),
    ("payment-service", "http_p99_latency_ms"): noisy(rng, 240, 20, S - timedelta(minutes=2), 10200, ramp=2),
    ("payment-service", "http_5xx_per_min"): noisy(rng, 25, 3, S - timedelta(minutes=1), 90, ramp=3),
    ("checkout-service", "http_5xx_per_min"): noisy(rng, 4, 1, S, 50, ramp=3),
})
for i in range(45):
    t = S - timedelta(minutes=1, seconds=30) + timedelta(seconds=i * 24 + rng.randint(0, 8))
    logs.append((t, "payment-service", "ERROR", "com.example.payments.client.HttpFraudCheckClient",
                 f"Fraud check failed for customer {rng.randint(1000, 9999)}",
                 'org.springframework.web.client.ResourceAccessException: I/O error on POST request for '
                 '"https://fraud.example.com/check": Read timed out; nested exception is '
                 'java.net.SocketTimeoutException: Read timed out', f"u145-{i:04d}"))
checkout_errors(rng, S, 20, "u145-")
add_baseline_traces(rng, "u145", S)
for i in range(40):
    t = S - timedelta(minutes=2) + timedelta(seconds=i * 26 + rng.randint(0, 9))
    tr = f"u145-{i:04d}"
    failed = i % 6 != 5
    fraud = 10000 if failed else rng.randint(7500, 9500)
    co = span(tr, None, "checkout-service", "POST /checkout", t, fraud + rng.randint(80, 140),
              "ERROR" if failed else "OK", error="502 Bad Gateway" if failed else None)
    root = span(tr, co, "payment-service", "POST /payments", t, fraud + rng.randint(20, 60),
                "ERROR" if failed else "OK", error="ResourceAccessException" if failed else None)
    span(tr, root, "payment-service", "POST /check", t, fraud, "ERROR" if failed else "OK", peer="fraud-api",
         error="SocketTimeoutException: Read timed out" if failed else None)
    if not failed:
        span(tr, root, "payment-service", "INSERT payments", t, rng.randint(3, 8), peer="payments-db")

# ---------------------------------------------------------------- INC-146: config change
rng = random.Random(146)
S = datetime(2026, 10, 21, 9, 5, tzinfo=timezone.utc)
CHANGED = S - timedelta(minutes=3)
incidents["incidents"].append({
    "id": "INC-146", "title": "payment-service 5xx elevated", "severity": "SEV2",
    "startedAt": iso(S), "detectedAt": iso(S + timedelta(minutes=4)),
    "affectedServices": ["payment-service"],
    "symptoms": ["HTTP 5xx on payment-service", "Checkout failures reported by customers"],
})
changes.append({"timestamp": iso(CHANGED), "service": "payment-service", "type": "CONFIG",
                "description": "spring.datasource.hikari.maximum-pool-size changed from 50 to 5 "
                               "(reduce database connection count)"})


def pool_connections(t):
    # Capped at the new pool size once the config change is live
    return 5.0 if t >= CHANGED else 12 + rng.uniform(-2, 2)


add_metrics(rng, S, {
    ("payments-db", "active_connections"): pool_connections,
    ("payment-service", "http_p99_latency_ms"): noisy(rng, 240, 20, S - timedelta(minutes=1), 29800, ramp=2),
    ("payment-service", "http_5xx_per_min"): noisy(rng, 25, 3, S, 100, ramp=3),
    ("checkout-service", "http_5xx_per_min"): noisy(rng, 4, 1, S + timedelta(minutes=1), 55, ramp=3),
})
for i in range(12):
    t = S - timedelta(minutes=1, seconds=20) + timedelta(seconds=i * 90 + rng.randint(0, 20))
    logs.append((t, "payment-service", "WARN", "com.zaxxer.hikari.pool.HikariPool",
                 f"HikariPool-1 - Pool stats (total=5, active=5, idle=0, waiting={rng.randint(60, 140)})", None, None))
for i in range(55):
    t = S + timedelta(seconds=5) + timedelta(seconds=i * 19 + rng.randint(0, 7))
    logs.append((t, "payment-service", "ERROR", "com.example.payments.service.PaymentService",
                 f"Charge failed for customer {rng.randint(1000, 9999)}", CHARGE_POOL_ERROR, f"p146-{i:04d}"))
checkout_errors(rng, S + timedelta(minutes=1), 25, "p146-")
add_baseline_traces(rng, "p146", S)
for i in range(12):
    # Refunds keep running at normal speed: the database itself is healthy
    t = S - timedelta(minutes=2) + timedelta(seconds=i * 110 + rng.randint(0, 20))
    tr = f"p146-r{i:04d}"
    root = span(tr, None, "payment-service", "POST /payments/{id}/refund", t, rng.randint(30, 55))
    span(tr, root, "payment-service", "SELECT payment_transactions", t, rng.randint(8, 16), peer="payments-db")
    span(tr, root, "payment-service", "UPDATE payments", t, rng.randint(3, 7), peer="payments-db")
for i in range(40):
    t = S + timedelta(seconds=5) + timedelta(seconds=i * 27 + rng.randint(0, 9))
    tr = f"p146-{i:04d}"
    failed = i % 8 != 7
    co = span(tr, None, "checkout-service", "POST /checkout", t, 30050 if failed else rng.randint(240, 330),
              "ERROR" if failed else "OK", error="502 Bad Gateway" if failed else None)
    root = span(tr, co, "payment-service", "POST /payments", t, 30000 if failed else rng.randint(170, 240),
                "ERROR" if failed else "OK", error="CannotCreateTransactionException" if failed else None)
    span(tr, root, "payment-service", "POST /check", t, rng.randint(95, 135), peer="fraud-api")
    if not failed:
        span(tr, root, "payment-service", "INSERT payments", t, rng.randint(4, 9), peer="payments-db")

# ================================================================ write files
(out / "incidents.json").write_text(json.dumps(incidents, indent=2) + "\n")

with open(out / "telemetry" / "changes.jsonl", "w", newline="\n") as f:
    for c in changes:
        f.write(json.dumps(c) + "\n")

with open(out / "telemetry" / "metrics.csv", "w", newline="\n") as f:
    f.write("timestamp,service,metric,value\n")
    for row in metric_rows:
        f.write(row + "\n")

logs.sort(key=lambda e: e[0])
with open(out / "telemetry" / "logs.jsonl", "w", newline="\n") as f:
    for t, service, level, logger, message, exception, trace in logs:
        entry = {"timestamp": iso(t), "service": service, "level": level, "logger": logger, "message": message}
        if exception:
            entry["exception"] = exception
        if trace:
            entry["traceId"] = trace
        f.write(json.dumps(entry) + "\n")

spans.sort(key=lambda s: (s["start"], s["spanId"]))
with open(out / "telemetry" / "traces.jsonl", "w", newline="\n") as f:
    for s in spans:
        f.write(json.dumps(s) + "\n")

print(f"incidents={len(incidents['incidents'])} changes={len(changes)} metrics={len(metric_rows)} "
      f"logs={len(logs)} spans={len(spans)}")
