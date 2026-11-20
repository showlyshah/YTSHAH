# ViewTube Advanced Roadmap

**Direction:** Advanced product scope with a hybrid AI architecture.  
**Compatibility baseline:** Android 10+ (current minSdk 29), Kotlin/Compose, Media3.  
**Rule:** Existing screens, playback, history, subscriptions, Watch later, sharing, comments and source integrations must remain available throughout the work.

This roadmap turns the agreed recommendations into 21 deliverables. It is an implementation plan, not a claim that every item is already shipped.

## Delivery gates

1. **Gate A — Make the current app build and install reliably.** Fix compiler errors without deleting or bypassing functionality. Require a debug APK artifact.
2. **Gate B — Regression baseline.** Exercise navigation, search, feeds, channel pages, playback, audio selection, history, subscriptions, Watch later, comments, SponsorBlock, PiP and background playback.
3. **Gate C — Playback and reliability.** Land player improvements in small, testable changes.
4. **Gate D — Hybrid intelligence.** Ship local intelligence first; add cloud AI through a provider interface and explicit user configuration.
5. **Gate E — Offline, personalisation and release hardening.** Add only source-permitted downloads, data portability and release checks.

## The 21 deliverables

| # | Deliverable | Priority | Acceptance criteria |
|---|---|---|---|
| 1 | Build and dependency stabilisation | P0 | Reproducible Gradle build and installable debug APK; no suppressed compiler failures |
| 2 | Existing-feature regression suite | P0 | Navigation, feeds, search, channels, player, history, subscriptions, Watch later and comments remain functional |
| 3 | Structured diagnostics and crash reporting | P0 | Redacted, local diagnostic events with export; no secrets or unnecessary personal data |
| 4 | Player gesture controls | P1 | Configurable brightness/volume gestures with accessible alternatives |
| 5 | Swipe seeking and playback speed presets | P1 | Accurate seeking and persistent speed selection |
| 6 | Background playback and media notification | P1 | Media session controls work after leaving the app and from notification controls |
| 7 | Picture-in-picture hardening | P1 | PiP entry/exit and playback lifecycle verified on supported Android versions |
| 8 | Audio-only mode | P1 | Video rendering can be suppressed while audio continues where the source supports it |
| 9 | Subtitle, quality and audio-track controls | P1 | Available tracks and qualities are presented safely; selection failures recover gracefully |
| 10 | Queue, playlist and resume position | P1 | Queue order, next/previous and per-video resume position persist |
| 11 | Adaptive playback recovery | P1 | Retry/backoff and actionable errors for network, extractor and stream failures |
| 12 | Local recommendation engine | P2 | Recommendations derive from opt-in local watch signals and can be reset/disabled |
| 13 | Natural-language and semantic search | P2 | Search can use indexed local metadata and fall back to ordinary source search |
| 14 | AI video summaries and key points | P2 | Summaries cite available transcript/metadata context and disclose when source text is unavailable |
| 15 | Ask questions about a video | P2 | Answers are grounded in available transcript/metadata; unsupported claims are identified |
| 16 | Hybrid AI provider coordinator | P2 | Local tasks work offline; cloud provider is configurable, optional and never required for core playback |
| 17 | AI diagnostics and repair suggestions | P2 | Classifies errors and proposes reviewable patches; never silently changes or merges production code |
| 18 | Source-permitted download manager and offline library | P3 | Downloads only where technically supported and permitted; queue, progress, retry and deletion work |
| 19 | Cache, storage and data-saver controls | P3 | Storage use is visible; cache cleanup is scoped and never deletes user library data unexpectedly |
| 20 | Backup, restore and data portability | P3 | Export/import of supported app data with validation and conflict handling |
| 21 | Personalisation, profiles, analytics and release hardening | P4 | Theme/feed/privacy controls, transparent viewing stats, compatibility/security checks and signed release process |

## Hybrid AI design

- **On-device:** metadata indexing, history lookup, lightweight topic classification, basic recommendation ranking, cache decisions and deterministic diagnostics.
- **Cloud (optional):** advanced language-model summaries, grounded Q&A and complex diagnostic reasoning.
- **Coordinator:** chooses local vs cloud execution, enforces consent/privacy rules, applies timeouts and fallback behavior, and records redacted audit events.
- **Provider credentials:** user-configured and stored with Android Keystore-backed encryption. Never embed keys in the APK, source tree, CI logs or repository secrets used by client builds.
- **No AI authority to publish code:** AI-generated fixes are proposals that require tests and human approval.

## Engineering constraints

- Preserve current user data; database changes require explicit, tested migrations.
- Keep network and extraction code behind repository interfaces.
- Keep playback lifecycle in the Media3 service/player layer, not UI composables.
- Avoid adding heavyweight dependencies without a measured benefit.
- Keep optional cloud AI and optional download features from blocking ordinary browsing and playback.
- Run build, unit tests and lint on each meaningful change. Upload debug APK only after successful assembly; publish release APK only after release validation.
- Test on the declared minimum Android version and a current Android release before calling a milestone complete.

## Current status

- [x] Existing CI workflow is configured to attempt a debug APK before later quality/release checks.
- [ ] Current compiler failure repaired and debug APK verified.
- [ ] Regression baseline completed.
- [ ] Deliverables 4–21 implemented and verified in staged releases.

The roadmap status must be updated only when the corresponding implementation and verification evidence exist.
