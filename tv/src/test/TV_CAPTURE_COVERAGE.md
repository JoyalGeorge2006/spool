# TV render capture handoff

Scope: actual TV production composables on a Robolectric television qualifier,
960x540 dp mdpi landscape. These are test previews, not installed-TV captures.
No production source changes. Patch applies after the supplied audio-only candidate
b85d7a51dd0978355fcdcf7e5ba32d3cec3c2f537721597c57ae388fe5d577c1
and its 25 deletion paths. Dependencies follow the phone's working render setup,
with MockK added to seed the final network-backed MainViewModel at its flow boundary.

## Run and artifact

Use Java 21 for test execution, Android SDK 37 for compilation as the existing TV
module specifies. Use the owner's current source materialization and compile cache.

    ./gradlew :tv:recordRoborazziProdDebug --tests '*TvPageScreenshotTest' -Pbitchord.bundleWear=false

Upload tv/build/outputs/roborazzi and tv/build/test-results. The recordRoborazzi
task matters: ordinary test execution may produce no PNGs. Keep every individual
PNG, not just a montage. File names end TEST_PREVIEW. Inspect actual resulting
pixels before delivery; unexecuted fixtures are not screenshot evidence.

## Planned method coverage

- Real TvApp root/chrome: Home, Explore, selected mood, Search, Settings.
- Home empty/error; Explore error.
- Library actual screen and sidebar: Recently Played, Liked Songs, Songs,
  Playlists, Albums, Artists; signed-out history. Library captures are standalone
  production screen renders and omit the root's top bar.
- Search idle/history, suggestions, mixed results, Songs filter, no results, error.
- Album, playlist, artist, Downloaded, On This Device and detail error, with
  actual TvDetailScreen but no root top bar. Local content is seeded, not a scan.
- All five setup pages using actual Continue/Not Now actions.
- Now Playing paused, seeded lyrics, nothing playing, Up Next, More.
- Song menu, Add to Playlist, New Playlist (without system keyboard), sleep timer.
- Equalizer bands/presets, Appearance, Refresh Rate, Scrobbling, Lyrics Sources,
  About, Account profiles, Account Settings, set-PIN entry.
- Optional phone-sign-in choice: not reachable through current TvApp call sites,
  because allowPhoneSignIn=false. Explicitly labelled optional route.

Long vertical content captures overlapping 65%-viewport increments until the
reported end, logging TV_SCROLL positions and failing on no progress. Horizontal
shelves show their initial visible cards, not every seeded card. MediaController
is null; playback/remote/navigation actions are not validated by these previews.
Artwork is null deliberately, so the app's genuine fallback is shown. Seeded
artist/song/account labels are fictional and no credential is used.

## Missing or conditional states, do not claim captured

- Google sign-in WebView, account credentials and live Google pages need runtime
  TV/browser evidence. No fake Google screen is substituted.
- Phone QR sign-in starts a local HTTP server and publishes actual device network
  addresses. Not invoked in host previews; also disabled in current root/setup.
- Android system keyboard, storage permission prompt, document pickers, USB/DAC,
  display refresh-rate capabilities, installer and device audio effects are not
  reproducible as actual device state here.
- Update/download/install UI is conditional on a live UpdateInfo and download
  service. Not seeded with an invented version or download URL.
- Account unlock/remove-PIN/sign-out-PIN and error/cooldown/confirm-PIN substates
  require test-lock seeding and additional interactions. Initial set-PIN entry is
  planned, but later PIN states are not yet covered.
- Scrobbling provider auth, successful playlist creation, authenticated changes,
  playback/error handling, timer expiry and full remote focus traversal are not
  accepted by this render suite.
- Ambient playing/auto-hide controls and AMOLED background variants are not yet
  covered. No blanket every-state or all-functionality claim is warranted.

## Source issues to surface, not silently fix

The frozen TV source still contains BitChord in welcome/setup copy, account copy,
About title, content descriptions, TV application labels and comments. The shared
logo may show Spool while TV text remains inherited. Preserve this in previews and
report it as found. This patch does not correct or conceal those production issues.

## Validation status at patch handoff

Static source/signature review and git diff --check only. No local Android SDK or
Java 21 was available in the patch worker, and CI dispatch belongs to the source
owner. Compilation, PNG production and actual-pixel review remain required.
