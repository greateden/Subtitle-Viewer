from __future__ import annotations

import json
import sys
import time
import tkinter as tk
from dataclasses import dataclass, field
from pathlib import Path
from tkinter import filedialog, messagebox


WINDOW_BG = "#121218"
PANEL_BG = "#1a1a20"
CARD_BG = "#22222a"
CARD_BORDER = "#3a3a44"
TEXT_BASE = "#f5f5f5"
TEXT_TIMED = "#c2c2c2"
TEXT_MUTED = "#d2d2dc"
ACTIVE_BG = "#ffd740"
ACTIVE_TEXT = "#202020"
MAX_VISIBLE_LINES = 3
TICK_MS = 40


@dataclass
class SubtitleToken:
    text: str = ""
    source_id: int | None = None
    start: float | None = None
    end: float | None = None
    estimated: bool | None = None

    @classmethod
    def from_dict(cls, data: dict) -> "SubtitleToken":
        return cls(
            text=data.get("text", ""),
            source_id=data.get("source_id"),
            start=data.get("start"),
            end=data.get("end"),
            estimated=data.get("estimated"),
        )

    def has_timing(self) -> bool:
        return self.start is not None and self.end is not None

    def is_active(self, time_seconds: float) -> bool:
        return self.has_timing() and self.start <= time_seconds <= self.end


@dataclass
class SubtitleLine:
    role: str = ""
    language: str = ""
    text: str = ""
    tokens: list[SubtitleToken] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: dict) -> "SubtitleLine":
        return cls(
            role=data.get("role", ""),
            language=data.get("language", ""),
            text=data.get("text", ""),
            tokens=[SubtitleToken.from_dict(token) for token in data.get("tokens", [])],
        )

    def display_name(self) -> str:
        if self.role and self.language:
            return f"{self.role} ({self.language.upper()})"
        if self.role:
            return self.role
        if self.language:
            return self.language.upper()
        return "Line"


@dataclass
class Sentence:
    index: int = 0
    start: float = 0.0
    end: float = 0.0
    mode: str = ""
    lines: list[SubtitleLine] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: dict) -> "Sentence":
        return cls(
            index=data.get("index", 0),
            start=float(data.get("start", 0.0)),
            end=float(data.get("end", 0.0)),
            mode=data.get("mode", ""),
            lines=[SubtitleLine.from_dict(line) for line in data.get("lines", [])],
        )

    def contains(self, time_seconds: float) -> bool:
        return self.start <= time_seconds <= self.end


@dataclass
class WordLinksProject:
    format: str = ""
    mode: str = ""
    sentences: list[Sentence] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: dict) -> "WordLinksProject":
        return cls(
            format=data.get("format", ""),
            mode=data.get("mode", ""),
            sentences=[Sentence.from_dict(sentence) for sentence in data.get("sentences", [])],
        )


class SubtitleLineView:
    def __init__(self, parent: tk.Widget) -> None:
        self.container = tk.Frame(parent, bg=CARD_BG, highlightthickness=1, highlightbackground=CARD_BORDER)
        self.label = tk.Label(
            self.container,
            text="Language",
            fg=TEXT_MUTED,
            bg=CARD_BG,
            font=("Segoe UI", 11, "bold"),
        )
        self.label.pack(fill="x", pady=(10, 4))

        self.text = tk.Text(
            self.container,
            wrap="word",
            height=4,
            bd=0,
            relief="flat",
            padx=16,
            pady=14,
            bg=PANEL_BG,
            fg=TEXT_BASE,
            insertbackground=TEXT_BASE,
            font=("Segoe UI", 18),
        )
        self.text.pack(fill="both", expand=True, padx=10, pady=(0, 10))
        self.text.configure(state="disabled", cursor="arrow")

        self.text.tag_configure("plain", foreground=TEXT_BASE, background=PANEL_BG)
        self.text.tag_configure("timed", foreground=TEXT_TIMED, background=PANEL_BG)
        self.text.tag_configure("active", foreground=ACTIVE_TEXT, background=ACTIVE_BG)

    def show(self) -> None:
        self.container.pack(fill="x", expand=False, pady=6)

    def hide(self) -> None:
        self.container.pack_forget()

    def clear(self) -> None:
        self.label.config(text="Language")
        self._set_readonly_text([])

    def render(self, line: SubtitleLine, active_time_seconds: float) -> None:
        self.label.config(text=line.display_name())
        parts: list[tuple[str, str]] = []

        if line.tokens:
            for index, token in enumerate(line.tokens):
                if index > 0:
                    parts.append((" ", "plain"))
                style = "active" if token.is_active(active_time_seconds) else "timed" if token.has_timing() else "plain"
                parts.append((token.text or "", style))
        else:
            parts.append((line.text or "", "plain"))

        self._set_readonly_text(parts)

    def _set_readonly_text(self, parts: list[tuple[str, str]]) -> None:
        self.text.configure(state="normal")
        self.text.delete("1.0", "end")

        for content, tag in parts:
            self.text.insert("end", content, tag)

        self.text.tag_add("center", "1.0", "end")
        self.text.tag_configure("center", justify="center")
        self.text.configure(state="disabled")


class SubtitleViewerApp:
    def __init__(self, initial_path: Path | None = None) -> None:
        self.root = tk.Tk()
        self.root.title("Wordlinks Subtitle Viewer")
        self.root.configure(bg=WINDOW_BG)
        self.root.minsize(1100, 700)

        self.project: WordLinksProject | None = None
        self.current_file: Path | None = None
        self.total_seconds = 0.0
        self.playback_seconds = 0.0
        self.playing = False
        self.last_tick = time.perf_counter()
        self.scrubbing = False

        self.file_var = tk.StringVar(value="No JSON loaded")
        self.current_time_var = tk.StringVar(value="00:00.00")
        self.active_sentence_var = tk.StringVar(value="No active subtitle")
        self.offset_var = tk.StringVar(value="Offset: 0 ms")
        self.speed_var = tk.StringVar(value="Speed: 1.00x")
        self.timeline_var = tk.DoubleVar(value=0.0)
        self.offset_ms_var = tk.IntVar(value=0)
        self.custom_speed_var = tk.StringVar(value="1.0")
        self.playback_speed = 1.0

        self.line_views: list[SubtitleLineView] = []
        self._build_ui()

        if initial_path is not None:
            self.load_project(initial_path)

        self.root.after(TICK_MS, self._tick)

    def _build_ui(self) -> None:
        top = tk.Frame(self.root, bg=WINDOW_BG)
        top.pack(fill="x", padx=16, pady=(16, 0))

        load_button = tk.Button(top, text="Load JSON", command=self.choose_file, padx=12, pady=6)
        load_button.pack(side="left")

        file_label = tk.Label(top, textvariable=self.file_var, fg=TEXT_BASE, bg=WINDOW_BG, anchor="w", font=("Segoe UI", 10))
        file_label.pack(side="left", fill="x", expand=True, padx=(12, 0))

        subtitle_frame = tk.Frame(self.root, bg=WINDOW_BG)
        subtitle_frame.pack(fill="both", expand=True, padx=16, pady=12)

        for _ in range(MAX_VISIBLE_LINES):
            line_view = SubtitleLineView(subtitle_frame)
            self.line_views.append(line_view)

        controls = tk.Frame(self.root, bg=WINDOW_BG)
        controls.pack(fill="x", padx=16, pady=(0, 16))

        info = tk.Frame(controls, bg=WINDOW_BG)
        info.pack(fill="x")

        tk.Label(info, textvariable=self.current_time_var, fg=TEXT_BASE, bg=WINDOW_BG, font=("Segoe UI", 11, "bold")).pack(side="left")
        tk.Label(info, textvariable=self.active_sentence_var, fg=TEXT_MUTED, bg=WINDOW_BG, font=("Segoe UI", 11)).pack(side="left", padx=(16, 0))

        timeline = tk.Scale(
            controls,
            from_=0.0,
            to=1.0,
            resolution=0.01,
            orient="horizontal",
            variable=self.timeline_var,
            showvalue=False,
            highlightthickness=0,
            troughcolor=CARD_BORDER,
            bg=WINDOW_BG,
            fg=TEXT_BASE,
            activebackground=ACTIVE_BG,
            command=self.on_timeline_change,
        )
        timeline.pack(fill="x", pady=(8, 8))
        timeline.bind("<ButtonPress-1>", self.on_scrub_start)
        timeline.bind("<ButtonRelease-1>", self.on_scrub_end)
        self.timeline = timeline

        buttons = tk.Frame(controls, bg=WINDOW_BG)
        buttons.pack(fill="x")

        self.play_pause_button = tk.Button(buttons, text="Play", command=self.toggle_playback, state="disabled", padx=12, pady=6)
        self.play_pause_button.pack(side="left")

        tk.Button(buttons, text="Restart", command=self.restart_playback, padx=12, pady=6).pack(side="left", padx=(8, 0))
        tk.Button(buttons, text="-1s", command=lambda: self.set_playback_seconds(self.playback_seconds - 1.0), padx=12, pady=6).pack(side="left", padx=(8, 0))
        tk.Button(buttons, text="+1s", command=lambda: self.set_playback_seconds(self.playback_seconds + 1.0), padx=12, pady=6).pack(side="left", padx=(8, 0))

        offset_controls = tk.Frame(buttons, bg=WINDOW_BG)
        offset_controls.pack(side="left", padx=(18, 0))
        tk.Label(offset_controls, text="Subtitle offset", fg=TEXT_BASE, bg=WINDOW_BG, font=("Segoe UI", 10)).pack(side="left")

        offset_scale = tk.Scale(
            offset_controls,
            from_=-5000,
            to=5000,
            resolution=50,
            orient="horizontal",
            variable=self.offset_ms_var,
            showvalue=False,
            highlightthickness=0,
            troughcolor=CARD_BORDER,
            bg=WINDOW_BG,
            fg=TEXT_BASE,
            activebackground=ACTIVE_BG,
            length=220,
            command=self.on_offset_change,
        )
        offset_scale.pack(side="left", padx=(10, 8))
        self.offset_scale = offset_scale

        tk.Label(offset_controls, textvariable=self.offset_var, fg=TEXT_MUTED, bg=WINDOW_BG, font=("Segoe UI", 10)).pack(side="left")

        speed_controls = tk.Frame(buttons, bg=WINDOW_BG)
        speed_controls.pack(side="left", padx=(18, 0))
        tk.Label(speed_controls, text="Playback speed", fg=TEXT_BASE, bg=WINDOW_BG, font=("Segoe UI", 10)).pack(side="left")
        tk.Button(speed_controls, text="1.0x", command=lambda: self.set_playback_speed(1.0), padx=10, pady=4).pack(side="left", padx=(10, 0))
        tk.Button(speed_controls, text="0.8x", command=lambda: self.set_playback_speed(0.8), padx=10, pady=4).pack(side="left", padx=(6, 0))
        tk.Button(speed_controls, text="0.5x", command=lambda: self.set_playback_speed(0.5), padx=10, pady=4).pack(side="left", padx=(6, 0))

        custom_speed_entry = tk.Entry(speed_controls, textvariable=self.custom_speed_var, width=6)
        custom_speed_entry.pack(side="left", padx=(8, 0))
        custom_speed_entry.bind("<Return>", lambda _event: self.apply_custom_speed())

        tk.Button(speed_controls, text="Set", command=self.apply_custom_speed, padx=10, pady=4).pack(side="left", padx=(6, 0))
        tk.Label(speed_controls, textvariable=self.speed_var, fg=TEXT_MUTED, bg=WINDOW_BG, font=("Segoe UI", 10)).pack(side="left", padx=(8, 0))

    def choose_file(self) -> None:
        initial_dir = self.current_file.parent if self.current_file else Path.cwd()
        selected = filedialog.askopenfilename(
            parent=self.root,
            title="Open subtitle JSON",
            initialdir=initial_dir,
            filetypes=[("JSON files", "*.json"), ("All files", "*.*")],
        )
        if selected:
            self.load_project(Path(selected))

    def load_project(self, path: Path) -> None:
        try:
            with path.open("r", encoding="utf-8") as handle:
                data = json.load(handle)
            self.project = WordLinksProject.from_dict(data)
        except Exception as exc:
            messagebox.showerror("Load error", f"Could not load JSON file:\n{exc}", parent=self.root)
            return

        self.pause_playback()
        self.current_file = path
        self.total_seconds = max((sentence.end for sentence in self.project.sentences), default=0.0)
        self.playback_seconds = 0.0
        self.timeline.configure(to=max(self.total_seconds, 1.0))
        self.timeline_var.set(0.0)
        self.file_var.set(f"{path.name} | format: {self.project.format or '-'} | mode: {self.project.mode or '-'}")
        self.root.title(f"Wordlinks Subtitle Viewer - {path.name}")
        self.play_pause_button.configure(state="normal" if self.total_seconds > 0.0 else "disabled")
        self.render_current_state()

    def toggle_playback(self) -> None:
        if self.playing:
            self.pause_playback()
        else:
            self.start_playback()

    def start_playback(self) -> None:
        if self.project is None:
            return
        if self.playback_seconds >= self.total_seconds:
            self.set_playback_seconds(0.0)
        self.playing = True
        self.last_tick = time.perf_counter()
        self.play_pause_button.configure(text="Pause")

    def pause_playback(self) -> None:
        self.playing = False
        self.play_pause_button.configure(text="Play")

    def restart_playback(self) -> None:
        self.pause_playback()
        self.set_playback_seconds(0.0)

    def on_scrub_start(self, _event: tk.Event) -> None:
        self.scrubbing = True

    def on_scrub_end(self, _event: tk.Event) -> None:
        self.scrubbing = False
        self.set_playback_seconds(self.timeline_var.get())
        self.last_tick = time.perf_counter()

    def on_timeline_change(self, raw_value: str) -> None:
        if self.project is None:
            return
        if self.scrubbing or not self.playing:
            self.set_playback_seconds(float(raw_value), update_scale=False)

    def on_offset_change(self, raw_value: str) -> None:
        self.offset_var.set(f"Offset: {int(float(raw_value))} ms")
        self.render_current_state()

    def set_playback_speed(self, speed: float) -> None:
        speed = max(0.05, speed)
        self.playback_speed = speed
        self.custom_speed_var.set(f"{speed:.2f}".rstrip("0").rstrip("."))
        self.speed_var.set(f"Speed: {speed:.2f}x")

    def apply_custom_speed(self) -> None:
        raw = self.custom_speed_var.get().strip()
        try:
            speed = float(raw)
        except ValueError:
            messagebox.showerror("Invalid speed", "Playback speed must be a number like 0.8, 0.5, or 1.25.", parent=self.root)
            return

        if speed <= 0:
            messagebox.showerror("Invalid speed", "Playback speed must be greater than 0.", parent=self.root)
            return

        self.set_playback_speed(speed)

    def set_playback_seconds(self, value: float, update_scale: bool = True) -> None:
        self.playback_seconds = max(0.0, min(value, self.total_seconds))
        if update_scale:
            self.timeline_var.set(self.playback_seconds)
        self.render_current_state()

    def render_current_state(self) -> None:
        self.current_time_var.set(self.format_time(self.playback_seconds))

        if self.project is None:
            self.active_sentence_var.set("No active subtitle")
            self.clear_lines()
            return

        subtitle_time = self.playback_seconds - (self.offset_ms_var.get() / 1000.0)
        sentence = self.find_sentence_at(subtitle_time)

        if sentence is None:
            self.active_sentence_var.set("No active subtitle")
            self.clear_lines()
            return

        self.active_sentence_var.set(f"Sentence {sentence.index} | subtitle time {subtitle_time:.2f} s")

        for index, line_view in enumerate(self.line_views):
            if index < len(sentence.lines):
                line_view.show()
                line_view.render(sentence.lines[index], subtitle_time)
            else:
                line_view.hide()
                line_view.clear()

    def clear_lines(self) -> None:
        for line_view in self.line_views:
            line_view.hide()
            line_view.clear()

    def find_sentence_at(self, subtitle_time: float) -> Sentence | None:
        if self.project is None:
            return None
        for sentence in self.project.sentences:
            if sentence.contains(subtitle_time):
                return sentence
        return None

    def _tick(self) -> None:
        if self.playing and not self.scrubbing:
            now = time.perf_counter()
            delta = now - self.last_tick
            self.last_tick = now
            self.set_playback_seconds(self.playback_seconds + (delta * self.playback_speed))
            if self.playback_seconds >= self.total_seconds:
                self.pause_playback()
        else:
            self.last_tick = time.perf_counter()

        self.root.after(TICK_MS, self._tick)

    @staticmethod
    def format_time(seconds: float) -> str:
        minutes = int(seconds // 60)
        remaining_seconds = seconds - (minutes * 60)
        return f"{minutes:02d}:{remaining_seconds:05.2f}"

    def run(self) -> None:
        self.root.mainloop()


def main() -> None:
    initial_path = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    app = SubtitleViewerApp(initial_path)
    app.run()


if __name__ == "__main__":
    main()
