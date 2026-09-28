# Cache files

Every line the plugin voices is saved to disk, so it is never billed twice. Each saved line is a
plain audio file behind a short header, and any tool that reads raw PCM can turn it into a WAV.

## Where they live

```
<RuneLite folder>/voiced-dialogue/cache/
```

The RuneLite folder is `%USERPROFILE%\.runelite` on Windows and `~/.runelite` on macOS and Linux.
There is one file per voiced line, with a `.tdc` extension.

## File names

A file's name is the lowercase hex SHA-256 of four fields: the backend id, the voice key, the
emotion, and the line's text. Each field is fed to the hash as its UTF-8 byte length (4-byte
little-endian int) followed by its UTF-8 bytes; a missing emotion is the text `null`.

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
which returns 24000 Hz, so `24000` is the value every current clip carries. A wrong value plays the
line too fast or too slow, at the wrong pitch.

Changing the output name to `clip.aiff` or `clip.au` produces those formats instead. WAV, AIFF and
AU are the formats Java sound plays, which is what other RuneLite plugins that accept custom
sounds expect.

## Things to know

- **Clips are dry.** Cave Echo is added at playback, so a clip never carries it.
- **Clips are not permanent.** The cache is capped by **Cache Size Limit** and deletes the oldest
  written files first once it passes the cap. Copy a clip somewhere else to keep it.
- **The format is stable.** Changing it would throw away every clip players already hold, so the
  plugin keeps reading and writing this layout.
