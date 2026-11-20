# Local listening history

Local recording is optional and off by default. It records qualified music listens
on this device, independently of Navidrome reporting and direct service submissions.
Enable it in Settings → Scrobbling & Statistics, in the separate local-history card.
Then choose Local Listening History in the existing Statistics from selector to
display its data on Home and in Listening Statistics. Enabling recording does not
change the selected statistics source or external sending destination.

Turning recording off preserves its database and restores the previous statistics
source if local history was selected. Removing external connections has no effect
on local records. History deletion requires confirmation and affects only local
records; already queued writes from the deleted history cannot recreate them.

## What is recorded

The existing music playback tracker supplies elapsed playing time. A listen
qualifies after half its known track length or four minutes, whichever is shorter.
Pauses, seeks and observation gaps greater than two seconds do not count as played
time. Repeat playback starts another event. A resumed qualified listen continues its
saved event when the library, track and playback position match an unfinished
checkpoint; paused time is not added. Unqualified progress is not reconstructed
after a process death and starts a new qualification period.

Each qualified listen has a unique event ID, original timestamp, metadata snapshot,
available library and recording identifiers, track length, and observed playing time.
Duration is checkpointed every five seconds and on pause/transition. Abrupt process
death can lose the last checkpoint interval; duration is never filled in from track
length or play count. Tracks without a usable duration/artist cannot qualify using
the existing tracker. No earlier provider or server history is imported automatically.

The existing dashboard renders these records, including measured listening time,
rankings, recent listens, activity, calendar and recorded listening patterns. Date
and hour aggregation uses the device's current local time zone, including daylight
saving boundaries. These are statistics for recorded local listens, not a claim to
complete listening history from other devices or times when recording was off.

## Storage and portability

A separate versioned Room database is kept in the application's private
`noBackupFilesDir`. It survives normal updates, is excluded from automatic cloud
backup/transfer, and has no foreign-key cascade from server/library deletion.
History is not uploaded or synchronized. Uninstallation, clearing app storage or
device loss can remove it; export regularly.

Export uses Android's document-save picker. Its ZIP contains a versioned manifest,
`stillshelf-history.jsonl` (the full portable record), `listens.jsonl` (ListenBrainz
listen-schema records), and a README. Export streams bounded database pages from
one transaction rather than loading an entire history into memory. It excludes
credentials, server addresses, streaming URLs and authenticated artwork URLs.
The archive contains personal listening history and should be kept private.

The ListenBrainz records use documented integer UNIX `listened_at` timestamps and
`track_metadata` fields. An import tool can submit these as batches with
`listen_type=import`. Track length is exported as `duration_ms`, separately from
StillShelf's observed `listened_ms`. No missing MusicBrainz IDs are invented.
Direct upload of this ZIP to ListenBrainz has **not** been verified and is not
guaranteed. Archive restoration into StillShelf is not implemented in this version.

Format references: [ListenBrainz JSON](https://listenbrainz.readthedocs.io/en/latest/users/json.html)
and [Core import API](https://listenbrainz.readthedocs.io/en/latest/users/api/core.html).
