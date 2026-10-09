# Kinship Admin

Run locally from this directory:

```powershell
npm.cmd run dev
```

Open `http://127.0.0.1:5173/` and sign in as `kinshipadmin`. This alias authenticates the Firebase account `demo@kinship.com` using its Firebase Authentication password. The role is verified from the server at `users/{Firebase Auth UID}`; that profile must have `admin: true` and must not be deleted. There is no signup flow and no password in the source or Firestore.

The signed-in dashboard loads the real `ACTIVE` and `FIXED` events from the same Firebase project as the Android app (`civicfix-32034`). Counts come from those records. It includes a Leaflet/OpenStreetMap map, category/ID search, a category dropdown defaulting to All events, sorting, and read-only fixed history. Clicking an event card selects its marker. The selected-event summary below the map shows its address instead of coordinates and shares cached/pending lookups with event details. “View details” opens a full-width section below the map and queue, containing the event title/ID, category, reports, unique reporters, severity, votes, coordinates, saved street/full address, timestamps and resolution record. Existing photos appear in a carousel; “View all” opens a photo gallery. Missing coordinates remain visible in the queue; general locations show their reported radius on the map. Location details use the saved `fullAddress`, `address`, `streetAddress`, or `locationName` string and look up an address when none exists. The current Android event model does not save these fields. On selecting a live event or opening its details, missing addresses are looked up using the authorized public Nominatim reverse API. Only coordinates are sent (no Firebase identity or token); failures retain the coordinates and offer a retry. OSM names identify the nearest indexed feature and may not be an exact postal address. Results are cached locally for 30 days (up to 250 addresses), and Web Locks plus shared local storage serialize tabs in this browser with at least 1.2 seconds between requests. Browser storage and Web Locks must be available. This owner-only configuration is for one administrator using one browser: before using multiple browsers/devices or sharing the panel, configure a proxy with an application-wide rate limiter and cache. `dist/geocoding-config.json` allows disabling lookup or changing the HTTPS reverse endpoint without changing application code. Keep requests below 1/second across the application, preserve application identification and attribution, and do not send confidential locations. See the [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).

Comments are read from `events/{eventId}/comments` only while details are open. The single-field newest-first listener displays only comments with `status: "active"`, respects `isAnonymous`, and unsubscribes when the event changes, details close, or the user signs out. Empty, loading, offline and retry states are included. Reporter profile documents and raw reports are not queried. The existing signed-in comment-read rule supports this view; no additional rules change is needed for comment display.

## Apply the resolution rules

`firestore.rules` is a complete copy of the user's supplied rules with these scoped changes:

- An administrator may change an event only from `ACTIVE` to `FIXED`, with exactly the allowed fields: `status`, `resolvedBy`, `resolvedAt`, and `updatedAt`.
- `resolvedBy` must equal the authenticated UID; both timestamps must equal the server request time.
- Deleted admin profiles lose admin permission, and event deletion is denied.
- Report aggregation is limited to `PENDING`/`ACTIVE` events so a fixed event cannot be reopened through that path.
- Vote-score and comment-count update paths now require authentication.

In Firebase Console, open project `civicfix-32034` → Firestore Database → Rules, replace the rules with this file's contents, and publish. Compare against any rules changed since the supplied version before replacing them. Website deployment does not publish Firestore rules. The authoring environment has no Firebase CLI login, so the rules have been tested locally but have not been deployed to the production project.

Alternatively, with Firebase CLI access to this project:

```powershell
firebase login
firebase deploy --only firestore:rules --project civicfix-32034 --config firebase.json
```

The existing rules still allow signed-in clients to adjust counters independently of their vote/comment documents and do not fully bind report aggregation to a newly created report. These pre-existing anti-abuse gaps are outside the resolution change; this file is not a full rules rewrite. Existing signed-in event reads are preserved.

## Resolution behavior

“Mark as Fixed” opens a confirmation dialog. A Firestore transaction rereads the protected profile and event, verifies that it is still active, and writes only the four resolution fields with server timestamps. Duplicate clicks are blocked while saving. A successful server acknowledgement closes the dialog; the realtime event query moves the event into fixed history. Permission/network errors preserve the visible event and allow retry. Offline sessions cannot resolve events. A revoked role closes the dashboard and clears the session.

The dashboard does not edit or delete child reports. The Android report repository reads linked event status on its next fetch. Fixed history exposes the stored administrator UID and resolution time; it does not infer a resolver identity from a reporter profile.

The single status query does not require a composite index. It loads all active/fixed records and is appropriate for the current small dataset. A large deployment needs pagination/count aggregation and bounded map queries.

`dist/firebase-config.mjs` currently uses the project's existing public configuration. If that API key is restricted to Android apps, register a Firebase Web app in the same project and replace the public configuration with its Web configuration. No service-account key belongs in this project.

## Validation

```powershell
npm.cmd test
```

The tests cover sign-in authorization, malformed event records, safe rendering, and the scoped resolution payload. Browser checks use isolated fixtures and cover full-screen desktop/mobile layouts, real map markers, cancellation, pending writes, failed-save retry, read-only history, empty data, offline state, and sign-out cleanup.

Run the Firestore security tests with Java 21+ and the isolated test dependencies:

```powershell
npm.cmd install --prefix .sites-runtime/rules-tests --no-audit --no-fund firebase-tools @firebase/rules-unit-testing firebase
node .sites-runtime/rules-tests/node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --only firestore --project demo-kinship --config firebase.json "node --test tests/firestore-rules.mjs"
```

These tests use a local demo project, never production records. A production sign-in and resolution have not been exercised because no account password or authorized Firebase CLI session was supplied. Validate an actual test event after publishing the rules.

The vendored Leaflet version is 1.9.4 (license included). Standard OSM tiles retain visible attribution and default browser caching; there is no tile prefetch or offline download. Map failures preserve access to the queue and coordinates. See [Leaflet documentation](https://leafletjs.com/) and the [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/).

## Release files

Deploy only `dist/`, together with `.openai/hosting.json` when using Sites. The sample dashboard preview and temporary QA/deployment artifacts have been removed. Keep `scripts/`, `tests/`, `package.json`, and Firebase rules/configuration for development and verification; these are outside the deployed static directory. Temporary `.sites-runtime/` contents can be regenerated for security tests or future deployments.

## Formatting

Run `npm.cmd ci` to install the pinned development formatter, then `npm.cmd run format` to format authored JavaScript, HTML, CSS, SVG, JSON and documentation. Use `npm.cmd run format:check` to check formatting without edits. Vendored Leaflet files and generated files are excluded. Firestore rules retain their existing syntax and indentation. These tools are development dependencies and are not deployed from `dist/`.
