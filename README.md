# Subtitle Wordlinks Viewer

This is a Python `tkinter` subtitle viewer for JSON files in the same format as:

`Zambo_Hoerspiele_fuer_Kinder_radio_AUDI20260415_NR_0022_684146339442420ca5187a1c4f5e92b1.parallel.wordlinks.json`

It is designed as a subtitle-only player:

- It shows the full sentence in each available language line.
- It highlights each annotated word when its token timestamp is active.
- It supports 2 or 3 subtitle lines automatically, depending on the JSON content.
- It includes audio loading, play/pause, timeline scrubbing, restart, and subtitle offset adjustment.
- It supports playback speed presets (`0.8x`, `0.5x`) and a custom playback speed for both subtitles and audio.

## Requirements

- Python 3.10 or newer
- `pygame` for audio playback

## Run

```bash
python subtitle_viewer.py
```

You can also open a file immediately:

```bash
python subtitle_viewer.py "path/to/file.json"
```

## Notes

- Negative offset shows subtitles sooner.
- Positive offset shows subtitles later.
- `pygame.mixer.music` does not provide real-time tempo stretching. Non-`1.0x` audio is played through a chunked pygame resampling fallback, so pitch changes with speed.

## Current Project Layout

- `subtitle_viewer.py` is the runnable Python app.
- The older Java files are still present in `src/main/java/`, but they are no longer required to run the viewer on this device.
