# Receipt reading prototype

I use this Python prototype to send images of one receipt to Gemini in a single request. It validates the returned values and compares them with an expected JSON file when one exists. The offline tests use a fake API.

## Reading a receipt

1. I use Python 3.10 or newer. Pillow is optional and lets the script resize large photos.
2. I put each document in its own folder under `private/receipts/`. For example, two photos of the same receipt can be named `sample/1.jpg` and `sample/2.jpg` there.
3. I set `GEMINI_API_KEY` in my environment or in the local `.env` file. Running the reader sends the selected images to Gemini.
4. From the repository root I run:

```sh
python prototype/reading/read_receipt.py private/receipts/sample
```

I can list available models with `--list-models` or choose one with `--model`. Raw responses, parsed JSON and the comparison report are written under `private/out/<model>/`. To score a document, I give its folder the same name as a JSON fixture in `testdata/reading/expected/`, without the extension.

## Offline checks

```sh
python -m unittest discover -s prototype/reading -v
```

The validator checks dates, amounts, line discounts, deposits, payments and totals. It flags unreadable values and keeps the original reading for review.
