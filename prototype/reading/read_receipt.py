"""S1 prototype: read receipts with Gemini, check the math, compare with expected readings.

Each document is a folder of images (all images of one receipt go in one request):

    private/receipts/lidl_2000-01-09/1.jpg
    private/receipts/lidl_2000-01-09/2.jpg

Run from the repo root:

    set GEMINI_API_KEY=...            (Windows cmd; PowerShell: $env:GEMINI_API_KEY="...")
    python prototype/reading/read_receipt.py private/receipts
    python prototype/reading/read_receipt.py private/receipts/lidl_2000-01-09 --model gemini-3.8-flash

Results go to private/out/<model>/: raw responses, parsed JSON and report.md.
If testdata/reading/expected/<folder name>.json exists, the reading is scored against it.
Only the Python standard library is required; Pillow is used to shrink big photos if installed.
"""
import argparse
import base64
import io
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import compare  # noqa: E402
import validate  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
API = "https://generativelanguage.googleapis.com/v1beta"
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".heic"}
MAX_SIDE = 3072  # px; long receipts need height, Gemini tiles big images anyway


def read_text(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def load_key():
    key = os.environ.get("GEMINI_API_KEY")
    env = os.path.join(REPO, ".env")
    if not key and os.path.exists(env):
        for line in read_text(env).splitlines():
            if line.startswith("GEMINI_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"')
    if not key:
        sys.exit("Set GEMINI_API_KEY (environment variable or GEMINI_API_KEY=... in .env in the repo root).")
    return key


def http(method, url, key, body=None, timeout=300):
    req = urllib.request.Request(url, method=method, headers={"x-goog-api-key": key, "Content-Type": "application/json"},
                                 data=json.dumps(body).encode() if body is not None else None)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)


def image_part(path):
    mime = mimetypes.guess_type(path)[0] or "image/jpeg"
    with open(path, "rb") as f:
        data = f.read()
    try:
        from PIL import Image
        im = Image.open(io.BytesIO(data))
        if max(im.size) > MAX_SIDE:
            im.thumbnail((MAX_SIDE, MAX_SIDE))
            buf = io.BytesIO()
            im.convert("RGB").save(buf, "JPEG", quality=90)
            data, mime = buf.getvalue(), "image/jpeg"
    except Exception:  # Pillow missing or unknown format: send the file as is
        pass
    return {"inline_data": {"mime_type": mime, "data": base64.b64encode(data).decode()}}


def read_document(images, model, key, prompt, schema):
    body = {
        "system_instruction": {"parts": [{"text": prompt}]},
        "contents": [{"role": "user", "parts": [image_part(p) for p in images] +
                      [{"text": f"{len(images)} image(s) of one document. Return the JSON."}]}],
        "generationConfig": {"temperature": 0, "responseMimeType": "application/json", "responseSchema": schema},
    }
    url = f"{API}/models/{model}:generateContent"
    for attempt in range(4):
        try:
            return http("POST", url, key, body)
        except urllib.error.HTTPError as e:
            msg = e.read().decode(errors="replace")
            if e.code in (429, 500, 503) and attempt < 3:
                wait = 20 * (attempt + 1)
                print(f"   HTTP {e.code}, retrying in {wait}s", flush=True)
                time.sleep(wait)
                continue
            hint = "  (model name unknown? run with --list-models)" if e.code == 404 else ""
            raise RuntimeError(f"HTTP {e.code}: {msg[:800]}{hint}") from None
        except (TimeoutError, urllib.error.URLError) as e:
            if attempt < 3:
                print(f"   network error ({e}), retrying", flush=True)
                time.sleep(10)
                continue
            raise RuntimeError(f"network error: {e}") from None


def documents(paths):
    for p in paths:
        p = os.path.abspath(p)
        imgs = sorted(f for f in os.listdir(p) if os.path.splitext(f)[1].lower() in IMAGE_EXT)
        if imgs:
            yield os.path.basename(p), [os.path.join(p, f) for f in imgs]
        else:  # parent folder: each subfolder is a document
            for sub in sorted(os.listdir(p)):
                if os.path.isdir(os.path.join(p, sub)):
                    yield from documents([os.path.join(p, sub)])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="*", default=[os.path.join(REPO, "private", "receipts")])
    ap.add_argument("--model", default=os.environ.get("GEMINI_MODEL", "gemini-3.5-flash-lite"))
    ap.add_argument("--out", default=os.path.join(REPO, "private", "out"))
    ap.add_argument("--expected", default=os.path.join(REPO, "testdata", "reading", "expected"))
    ap.add_argument("--delay", type=float, default=6.0, help="seconds between requests (free tier rate limit)")
    ap.add_argument("--list-models", action="store_true")
    a = ap.parse_args()

    key = load_key()
    if a.list_models:
        for m in http("GET", f"{API}/models?pageSize=200", key).get("models", []):
            if "generateContent" in m.get("supportedGenerationMethods", []):
                print(m["name"].split("/", 1)[1], "-", m.get("displayName", ""))
        return

    prompt = read_text(os.path.join(HERE, "prompt.md"))
    schema = json.loads(read_text(os.path.join(HERE, "schema.json")))
    out_dir = os.path.join(a.out, a.model)
    os.makedirs(out_dir, exist_ok=True)

    report = io.StringIO()
    report.write(f"# Reading report - {a.model} - {time.strftime('%Y-%m-%d %H:%M')}\n\n```\n")
    summary = []
    docs = list(documents(a.paths))
    for n, (name, images) in enumerate(docs):
        if n:
            time.sleep(a.delay)
        print(f"[{n + 1}/{len(docs)}] {name} ({len(images)} images)", flush=True)
        t0 = time.time()
        try:
            resp = read_document(images, a.model, key, prompt, schema)
        except Exception as e:  # keep going with other documents
            print(f"   FAILED: {e}")
            report.write(f"== {name}: READ FAILED: {e}\n")
            summary.append((name, "READ_ERROR", None, None, None))
            continue
        secs = time.time() - t0
        write_json(os.path.join(out_dir, f"{name}.raw.json"), resp)
        try:
            text = resp["candidates"][0]["content"]["parts"][0]["text"]
            doc = json.loads(text)
        except (KeyError, IndexError, json.JSONDecodeError) as e:
            print(f"   bad response: {e}")
            report.write(f"== {name}: BAD RESPONSE ({e})\n")
            summary.append((name, "READ_ERROR", None, None, None))
            continue
        write_json(os.path.join(out_dir, f"{name}.json"), doc)
        usage = resp.get("usageMetadata", {})
        purchase, rep = validate.normalize(doc)
        validate.print_report(name, purchase, rep, report)
        report.write(f"   {secs:.1f}s, tokens in {usage.get('promptTokenCount')}, out {usage.get('candidatesTokenCount')}\n")
        exp_path = os.path.join(a.expected, f"{name}.json")
        acc = items = None
        if os.path.exists(exp_path):
            c = compare.compare(doc, json.loads(read_text(exp_path)))
            compare.print_comparison(name, c, report)
            acc = c["itemFieldAccuracy"]
            items = f"{c['itemsMatched']}/{c['itemsExpected']}"
        report.write("\n")
        summary.append((name, purchase["sumCheck"], items, acc, len(rep.errors)))
        print(f"   sumCheck {purchase['sumCheck']}, items {items}, fields {acc:.0%}" if acc is not None
              else f"   sumCheck {purchase['sumCheck']}", flush=True)

    report.write("```\n\n| Document | Sum check | Items matched | Item fields | Errors |\n|---|---|---|---|---|\n")
    for name, sc, items, acc, errs in summary:
        report.write(f"| {name} | {sc} | {items or '-'} | {f'{acc:.0%}' if acc is not None else '-'} | {errs if errs is not None else '-'} |\n")
    path = os.path.join(out_dir, "report.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write(report.getvalue())
    print(f"\nReport: {path}")


if __name__ == "__main__":
    main()
