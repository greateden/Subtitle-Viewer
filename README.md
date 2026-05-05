# Subtitle Wordlinks Viewer

This is a Java Swing subtitle viewer for JSON files in the same format as:

`Zambo_Hoerspiele_fuer_Kinder_radio_AUDI20260415_NR_0022_684146339442420ca5187a1c4f5e92b1.parallel.wordlinks.json`

It is designed as a subtitle-only player:

- It shows the full sentence in each available language line.
- It highlights each annotated word when its token timestamp is active.
- It supports 2 or 3 subtitle lines automatically, depending on the JSON content.
- It includes play/pause, timeline scrubbing, restart, and subtitle offset adjustment.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer

## Run

```bash
mvn package
java -jar target/subtitle-wordlinks-viewer-1.0.0.jar
```

You can also open a file immediately:

```bash
java -jar target/subtitle-wordlinks-viewer-1.0.0.jar "path/to/file.json"
```

## Notes

- Negative offset shows subtitles sooner.
- Positive offset shows subtitles later.
- This app does not play audio or video. It only simulates subtitle playback on a timing bar.
