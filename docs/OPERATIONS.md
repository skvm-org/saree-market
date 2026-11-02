# Operations: reading the logs

Production is one EC2 instance running two containers, `backend` (the Spring Boot API) and `caddy` (HTTPS in front of it). The database is the shared RDS instance. This page is how to find out what the backend is doing.

## Get to the logs

The deploy runs from GitHub Actions against the instance's Docker daemon, so there are no compose files on the instance. Log in and use `docker` directly:

```bash
ssh <user>@<ec2-host>
docker ps --format '{{.Names}}  {{.Status}}'        # find the container names
docker logs --since 1h $(docker ps -qf name=backend)
```

Production logs are JSON, one object per line. Pipe them to `jq` (install it on the instance with `sudo yum install -y jq` or `sudo apt install -y jq`):

```bash
B=$(docker ps -qf name=backend)

docker logs --since 1h $B | jq -r '"\(.["@timestamp"]) \(.level) [\(.requestId // "-")] \(.message)"'   # readable
docker logs --since 1h $B | jq 'select(.level == "ERROR")'                                              # errors with stack traces
docker logs --since 1h $B | jq 'select(.level == "WARN" or .level == "ERROR") | {message, requestId}'   # anything unusual
docker logs -f $B | jq -r '.message'                                                                    # follow live
```

## Follow one request

Every response has an `X-Request-Id` header, and every error body has the same value as `requestId`. When someone reports a failure, ask for that id (or read it from the response) and pull every line of that request:

```bash
docker logs --since 24h $B | jq -c 'select(.requestId == "d7fdf5800025")'
```

A request normally leaves two or three lines: what the code did (for example `Collection 14 created by seller 18 with 1 photos`), why it was refused (`refused with INVALID_CREDENTIALS …`), and the closing line `POST /api/v1/collections 201 167ms user=18 SELLER`. The apps can also send their own `X-Request-Id` (letters, digits, `-` and `_`, up to 64 characters), which is reused as is.

## What the levels mean

| Level | What it is | Look at it when |
| --- | --- | --- |
| ERROR | A server fault: an unhandled exception (with stack trace), an S3 call that failed, any 5xx | Always. Each one is a bug or an outage |
| WARN | Refused or suspicious but handled: 401, 403, 429, 409, failed logins, lockouts, a user who is disabled | A spike means guessing, a broken client, or a bad deploy |
| INFO | Normal traffic and events: request lines, registrations, logins, orders changing status, expiry job counts | Reconstructing what happened |
| DEBUG | Off in production | Turn on briefly, see below |

## First lines to read

- **After a deploy:** the line `Backend ready: build=<commit> profiles=… storage=s3 bucket=saree-market fallbackToLocal=true schema=<n>` says which commit runs, whether photos go to S3, and the database schema version. If `storage=local` or `build=unknown`, the deploy settings are wrong.
- **Photos or payment proofs not saving:** search for `S3 save failed`. The line ends with the AWS error code. `AccessDenied` means the instance role is missing or its policy (`deploy/s3-iam-policy.json`) does not cover the bucket.
- **Photos not showing:** `Primary storage failed for … reading the old folder instead` means S3 could not be read and the app fell back to the old local folder.
- **Someone cannot log in:** `Login failed for phone ******1234 (wrong password)` and `Logins locked for phone …` use the last four digits of the phone number, never the whole number or the password.
- **Orders:** `Order 42 is now CONFIRMED` for every status change, `Expiry run: 2 holds released, 1 orders cancelled` from the five-minute job.

Nothing sensitive is logged: no passwords, tokens, request bodies, full phone numbers, addresses or UPI IDs.

## Turn up the detail for a short time

Set `LOGGING_LEVEL_COM_SAREEMARKET=DEBUG` on the backend service in `deploy/docker-compose.yml`, merge to redeploy, and remove it when done. It adds, for example, a line for every photo saved to S3. Do not leave it on.

## Disk use

Docker keeps each container's output on the instance disk. `deploy/docker-compose.yml` caps it: 5 files of 10 MB per container (50 MB each), oldest dropped first. That is roughly the last day or two of a quiet app, less when busy. Logs older than that are gone, so look soon after an incident, or copy what you need:

```bash
docker logs --since 6h $B > /tmp/backend-incident.log
df -h /var/lib/docker                                   # how full the disk is
```

## Not set up yet

- **Longer history and alerts:** logs live only on the instance and are lost if it is replaced. Shipping them to CloudWatch Logs with an alarm on errors is phase C in `docs/plans/backend-logging.md`.
- **Caddy access log:** Caddy only logs its own errors. Enabling its access log would show requests that never reach the backend, for example during a 502 when the backend is down.
