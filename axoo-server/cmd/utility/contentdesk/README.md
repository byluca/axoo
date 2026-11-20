# Content Desk

A local tool for managing content, test profiles, and activity reports.

## Run

Set up the backend environment, then run this from `axoo-server/`.
See the [backend guide](../../../README.md).

```sh
go run ./cmd/utility/contentdesk
```

Open the private localhost URL printed in the terminal. The desk uses the
Android Axooji catalog at `../axoo-kotlin/app/src/main/assets/axooji`.
If the catalog is elsewhere, pass `-emoji-assets /path/to/axooji`.
See the [maintenance scripts](../../../scripts/README.md) to rebuild the previews.

For push notifications, set `FCM_SERVICE_ACCOUNT_JSON` or
`FCM_SERVICE_ACCOUNT_PATH`. You can also use `FCM_PROJECT_ID` with Google
Application Default Credentials.

## Test profiles

Create up to 300 test profiles in the UI or run:

```sh
go run ./cmd/utility/contentdesk -add-profiles 20 -profile-campus CAMPUS_UUID -profile-gender female
```

Use `-rename-profiles COUNT` or `-repair-profile-nicknames COUNT` with
`-rename-profiles-since RFC3339_DATE` to update an existing batch.

Activity reports use the `Europe/Rome` time zone and exclude admins, alpha
testers, and profiles managed by the desk.
