# Push backend

Push installations live in PostgreSQL in the `push_installations` table.
Account deletion removes them through the foreign key.

## Configuration

Keep the existing `FCM_SERVICE_ACCOUNT_JSON` environment variable containing the
service account JSON. The Admin SDK reads the credentials and derives the Firebase
project from its `project_id` field. `FCM_PROJECT_ID` is optional and overrides the
target project when supplied; it is useful if credentials belong to another project.

Alternatively, set only `FCM_PROJECT_ID` to use Application Default Credentials:
the runtime service account on Google Cloud, or `GOOGLE_APPLICATION_CREDENTIALS`
pointing to a service account JSON file. Credentials must have permission to send
FCM messages for the target project, whose Cloud Messaging HTTP v1 API must be
enabled. Keep credential files outside the repository.

Without either `FCM_SERVICE_ACCOUNT_JSON` or `FCM_PROJECT_ID`, registration endpoints
remain available but sending is disabled. Invalid initialization fails startup.
Use the same Firebase project for both apps. For iOS, configure its APNs key and
bundle ID in Firebase before testing delivery.

## Client contract

Both endpoints require the existing authenticated session. Generate a random UUID
once per app installation and keep it across logins and registration refreshes.

- `PUT /api/v1/users/me/push-installations/{installation_id}`
- `DELETE /api/v1/users/me/push-installations/{installation_id}`

Both accept the same JSON body and return `204` on success:

```json
{"registration": "<current FCM registration>"}
```

Upload after login and when the FCM registration changes; retry a failed upload
on the next authenticated app start. PUT updates the installation, including its
account association, and keeps each FCM registration associated with one row.
DELETE must run before discarding the authenticated session on logout. It removes
only a matching account, installation and registration, so a delayed logout cannot
erase a newer binding. DELETE is also allowed for suspended/banned accounts.
On logout, also ask the FCM SDK to invalidate its registration; complete a failed
invalidation when connectivity returns. Until cleanup reaches the API or FCM, the
device may still receive a generic push for its previous account.

## Events and payload

A new top-level comment notifies the post owner (`post_comment`). A reply notifies
only the parent comment owner (`comment_reply`). The server skips self-interactions,
recipients who blocked the commenter or post author, hidden posts, unavailable
content, and banned or suspended recipients. Retrying comment creation with the
same `client_id` does not send again.

Messages contain a generic title/body and these data fields:

- `type`: `post_comment` or `comment_reply`.
- `post_id`, `comment_id`: navigation target and event identifier.
- `recipient_id`: ignore navigation if a different account is currently signed in.

Android must create the `interactions` notification channel. iOS receives an alert
with the default sound. Both apps must request permission and handle notification
taps, including cold startup and delayed session restoration. Opening a post uses
the existing API and its current visibility checks.

Sending runs after the comment transaction commits, with a three-second total
timeout covering lookup and all installations. It may add up to that timeout to
the comment request. Errors are logged and do not undo the saved comment. Only
FCM `UNREGISTERED` responses remove registrations; transient errors and invalid
payloads do not. Delivery is best effort: there is no persistent queue or retry,
so an outage or process shutdown can lose a push.

Startup logs whether push delivery is enabled. Each new comment logs the number
of matching installations and FCM acceptance, expired registration, or failure
with its HTTP status and timeout flag. `installations=0` means no eligible device;
`FCM accepted` confirms acceptance by Firebase, not display on the device. Logs
do not include registration addresses, credentials, or comment text.

Unit tests run with `go test ./...`. Database tests require a disposable local
PostgreSQL database via `AXOO_PUSH_TEST_DSN`; they create and remove their own schema.

References: [Firebase Admin sending](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk),
[FCM registration management](https://firebase.google.com/docs/cloud-messaging/manage-tokens).
