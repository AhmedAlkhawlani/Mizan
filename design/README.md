# Concept board

`concept-board.html` is a self-contained 1600×1000 design board. To regenerate
the PNG without opening a browser:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new --disable-gpu --hide-scrollbars \
  --user-data-dir=/tmp/chrome-mizan \
  --window-size=1600,1000 \
  --screenshot="$PWD/concept-board.png" \
  "file://$PWD/concept-board.html"
```

The board mirrors the actual product direction: dashboard, budget, Data Saver,
QS tile, persistent notification and a 2×2 widget.
