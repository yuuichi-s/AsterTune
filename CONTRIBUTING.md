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
visit [our Weblate page](https://hosted.weblate.org/projects/yuuichi-s-outertune/).
Thanks to Weblate for offering free hosting to open-source projects!

### Important

- Translators should submit strings through Weblate, however if there are changes that cannot be accomplished via
  Weblate, you may submit a pull request manually
- Make sure new strings are in the right place, which is `strings-ot.xml` for AsterTune strings
- If they are ported from upstream InnerTune (ex. when syncing upstream), they go into `strings.xml`

## Submitting a pull request

To make everyone's life easier, there are a set of guidelines that are to be followed when submitting pull requests.

- One pull request for one feature/issue, please refrain from tackling many features/issues in one pull request
- Write a descriptive title and a meaningful description
- Upload images/video for any UI changes
- In the event of merge conflicts, you may be required to rebase onto the current `dev` branch
- **You are required to build and test the app before submitting a pull request**

## Committing guidelines

- Prefix commits with tags, and provide descriptions if necessary. These are generally done in format:
  `tag: commit_name`. [Example](https://github.com/OuterTune/OuterTune/commit/798e8366227dd2cc38355224c733dbf7e8ffcee0)
    - A list of tags commonly used is provided below
- Commit descriptions are not required, but highly recommended
- When porting or cherry-picking from other repositories or sources:
    - Maintain
      authorship. [Example](https://github.com/OuterTune/OuterTune/commit/b0dc59682190b41f0200e9df5174322acaa3d40d)
    - If this is not possible please provide the source in the commit
      description. [Example](https://github.com/OuterTune/OuterTune/pull/59/commits/e40325dd86ac2c30347cfd4f9e92bbf15a0d0c82)
- IMPORTANT: When merging `dev` into your branch
    - Preferred: rebase your branch onto `dev`
    - Discouraged: merging `dev` into your branch with a merge commit, as it makes the commit history harder to follow.
      If you do this, your pull request will be rebased/squashed and merged
- IMPORTANT: Merge conflicts
    - As per the previous point, please rebase and conflicts are to be resolved within the commits themselves
    - We may ask you to rebase your PR if merge conflicts are an issue when merging
    - In the event you do not wish to rebase, your pull request will be squashed and merged as stated above
- If database schema changes are required, please state clearly if a version increment is required. Additional details
  are in the `Database schema changes` section
- For multi-part commits where all parts are required for functionality, use
  `[1/2], [2/2], etc`. [See example](https://github.com/OuterTune/OuterTune/pull/59/commits)
- Do not edit the translation files directly; they are managed through Weblate.

### Tags

| Tag (General) | Description                                                                                         |
|---------------|-----------------------------------------------------------------------------------------------------|
| github        | GitHub facing configs, ex. build scripts, templates, etc.                                           |
| gradle        | Dependency/library updates                                                                          |
| readme        | Readme changes                                                                                      |
| fixup         | Amendments to certain commits. this is generally done in the format `fixup: <old commit name here>` |

| Tag (ui) | Description                                                                        |
|----------|------------------------------------------------------------------------------------|
| ui       | User interface                                                                     |
| library  | Library general components. Use the specific ones below if it is a specific change |
| artist   | Playlist screens & components                                                      |
| album    | Album screens & components                                                         |
| playlist | Playlist screens & components                                                      |
| songs    | Song screens & components                                                          |

| Tag (Playback) | Description               |
|----------------|---------------------------|
| player         | Music playback components | 
| multiqueue     | Queue components          |

| Tag (Features) | Description                 |
|----------------|-----------------------------|
| sync           | YouTube Music sync features |
| downloads      | Offline song downloads      |
| innertube      | Innertube module            |

| Tag (Local Media) | Description                      |
|-------------------|----------------------------------|
| lm                | General local library components | 
| scanner           | Local media scanner              |

| Tag (Misc)    | Description                                                                                     |
|---------------|-------------------------------------------------------------------------------------------------|
| `<file name>` | Changes for a single file that do not fit into any other tag                                    |
| app           | General changes, or anything that does not fit any other tags. (For within the app module only) |
| astertune     | General changes that span across multiple modules                                               |
| fastlane      | Fastlane components                                                                             |
| translations  | General changes to translation components                                                       |

- Please use a tag if it already exists, however, if you are developing a new major feature, you are free to assign your
  own appropriate tag
- Tags can be stacked. For example: `ui: library: Something something`

## Database schema changes

- Clearly state if a database version increment is required
- You are required to make sure migration works from the previous database version
- All commits that modify the database version should be contained in a single commit
    - These include generating JSON, Entity classes, migration conflict resolution, bumping database version etc.
