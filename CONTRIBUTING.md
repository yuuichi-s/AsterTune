> [!NOTE]
> The Markdown documentation will be updated progressively.

# Building

When cloning the repository, please clone submodules recursively.

```bash
git clone --recurse-submodules <url>
```

If you run into unexpected errors related to the submodules, updating them may resolve the issue. The `media` submodule is required:

```bash
git submodule update --init --recursive
```

For most users, we recommend importing and building through Android Studio.

## Build flavors/variants

There are the following build flavors

```
core
full
```

| Flavor  | Architecture support                | Version update checker | FFmpeg audio decoders |
|---------|-------------------------------------|------------------------|-----------------------|
| core    | arm64-v8a, armeabi-v7a, x86, x86_64 | ❌                      | ❌                     |
| full    | arm64-v8a, armeabi-v7a, x86, x86_64 | ✅                      | ✅                     |

Tag extraction uses TagLib in all flavors. The `full` flavor additionally bundles ffMetadataEx
(prebuilt AAR at `prebuilt/ffMetadataEx-release.aar`) for the FFmpeg audio decoders, which enable
playback of extended codecs (e.g. ALAC/APE/WavPack/DSD). No extra setup is required to build the
`full` flavor.

## Building from the command line

```bash
# core debug build
./gradlew assembleCoreDebug

# full debug build
./gradlew assembleFullDebug

# build and install the full debug build to a connected device
./gradlew installFullDebug
```

<br/><br/>

# Contributing to AsterTune

## Translations

We use Weblate to translate AsterTune. For more details or to get started,
visit [our Weblate page](https://hosted.weblate.org/projects/yuuichi-s-astertune/).
Thanks to Weblate for offering free hosting to open-source projects!

- Submit translations through Weblate whenever possible.
- Add new AsterTune source strings to `strings-ot.xml`.
- Do not edit Weblate-managed localized resource files directly. If a required change cannot be made through Weblate,
  explain why in the pull request.

## Submitting a pull request

- Keep each pull request focused on one feature, fix, or maintenance task.
- Use a descriptive title and explain what changed and why.
- Include before-and-after screenshots or a screen recording for UI changes.
- Target the `dev` branch, keep your branch up to date, and resolve merge conflicts before the pull request is merged.
- Build the affected variant and run the relevant tests before submitting the pull request.
- List the checks you performed and their results. Clearly state any checks that were not performed.

## Commit guidelines

- Use the `type: subject` format, such as `feat: add ...`, `fix: prevent ...`, or `docs: update ...`.
- Choose a type that reflects the effect of the change. Common types include `feat`, `fix`, `docs`, `refactor`, `test`,
  `build`, and `chore`.
- Keep commits focused and use the commit body when the reason for a change is not clear from the subject.
- When porting or cherry-picking code from another source, preserve the original authorship whenever possible.
  Otherwise, identify the source repository and commit in the commit body.

## Database schema changes

- Clearly state whether the database version must be incremented.
- Provide a migration from the previous database version and verify that it works.
- Update the affected entities, migration code, database version, and generated schema JSON together in the same pull request.
