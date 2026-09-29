# Cache files

Every line the plugin voices is saved to disk, so a repeated line is played from disk instead of
billed again. Each saved line is a plain audio file behind a short header, and any tool that reads
raw PCM can turn it into a WAV.

## Where they live

```
<RuneLite folder>/voiced-dialogue/cache/
```

The RuneLite folder is `%USERPROFILE%\.runelite` on Windows and `~/.runelite` on macOS and Linux.
There is one file per voiced line, with a `.tdc` extension.

## File names

A file's name is the lowercase hex SHA-256 of four fields: the cache namespace (`cloud-speech` for
both providers), the voice key, the emotion's upper-case name (such as `HAPPY`), and the line's
text. Each field is fed to the hash as
its UTF-8 byte length (4-byte little-endian int) followed by its UTF-8 bytes; when there is no
emotion, that field is the text `null`.

For both cloud providers the voice key is the resolved voice id, then `|a` and the speaker token
(`MALE`, `FEMALE`, `CHILD_MALE`, `CHILD_FEMALE`, or `NARRATOR`), then `|s` and the speaking pace
percent when it is not 100, then `|p` and the profile hash, then `|l` and the language token when
the line is translated (the `SpokenLanguage` enum name, plus `+` and the `SpeakingStyle` enum name
when a speaking style applies). The profile hash is the first 16 lowercase hex characters of the
SHA-256 of a UTF-8 string: the name, accent, style, and pace joined by `U+0001`, then `U+0001` and
the pitch when one is sent, then `U+0002` and the accent detail when one is sent, then `U+0003` and
the age when the profile has one. Each field is taken
as sent: surrounding whitespace and trailing `.`, `;`, `,`, and `:` removed, a field left empty
counts as absent and is written as `null`, and the accent detail is sent only with an accent.

The name therefore says nothing a person can read. Finding one particular line means listening,
though sorting by modified time helps: the newest file is the most recent line that had to be
synthesized. A line played from the cache does not touch its file.

## Layout

A 16-byte header, then the audio. Every number is little-endian.

| Offset | Size | Field |
|--------|------|-------|
| 0 | 4 | Magic `0x54444331`, which reads `1CDT` on disk |
| 4 | 4 | Sample rate in Hz (int32) |
| 8 | 4 | Sample count (int32) |
| 12 | 4 | Reserved, always `0` |
| 16 | 4 × sample count | Samples, mono float32 in `-1.0` to `1.0` |

A file whose size is not exactly `16 + 4 × sample count` is treated as corrupt and deleted on its
next read.

## Convert a clip to WAV

With [ffmpeg](https://ffmpeg.org/) installed, this works the same on Windows, macOS and Linux:

```
ffmpeg -f f32le -ar 24000 -ac 1 -skip_initial_bytes 16 -i <file>.tdc clip.wav
```

`-ar` must match the sample rate in the file's header (offset 4). Both providers use Gemini speech,
which returns 24000 Hz, so `24000` is the value every clip carries. A wrong value plays the line
too fast or too slow, at the wrong pitch.

Changing the output name to `clip.aiff` or `clip.au` produces those formats instead. WAV, AIFF and
AU are the formats Java sound plays.

## Things to know

- **Clips are dry.** Cave Echo is added at playback, so a clip never carries it.
- **Clips are not permanent.** The cache is capped by **Cache Size Limit** and deletes the oldest
  written files first once it passes the cap. Copy a clip somewhere else to keep it.
- **The format is stable.** Changing it would throw away every clip players already hold, so the
  plugin keeps reading and writing this layout.
