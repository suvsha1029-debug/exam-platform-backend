#!/usr/bin/env python3
"""
Download image resources discoverable from a site.

This crawler starts from a URL, walks same-origin HTML/CSS/JS resources,
extracts image-like URLs, and downloads them to a local folder.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import posixpath
import re
import sys
import time
from collections import deque
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qsl, unquote, urljoin, urlparse, urlsplit, urlunsplit
from urllib.request import Request, build_opener


IMAGE_EXTENSIONS = {
    ".apng",
    ".avif",
    ".bmp",
    ".gif",
    ".ico",
    ".jfif",
    ".jpeg",
    ".jpg",
    ".png",
    ".svg",
    ".tif",
    ".tiff",
    ".webp",
}

HTML_EXTENSIONS = {"", ".html", ".htm", ".xhtml", ".php", ".asp", ".aspx", ".jsp"}
TEXT_RESOURCE_EXTENSIONS = HTML_EXTENSIONS | {".css", ".js", ".mjs"}
SKIP_SCHEMES = {"data", "blob", "mailto", "tel", "javascript"}
URL_PATTERN = re.compile(
    r"""(?P<quote>["'])?(?P<url>
    (?:
        https?://[^"'()\s]+|
        //[^\s"'()]+|
        /[^"'()\s]+|
        \./[^"'()\s]+|
        \.\./[^"'()\s]+|
        [A-Za-z0-9_\-./%]+?\.(?:png|jpe?g|gif|svg|webp|bmp|ico|avif|tiff?)(?:\?[^"'()\s]*)?
    ))(?P=quote)?""",
    re.IGNORECASE | re.VERBOSE,
)
CSS_URL_PATTERN = re.compile(r"url\((?P<value>[^)]+)\)", re.IGNORECASE)


def guess_extension_from_content_type(content_type: str | None) -> str:
    if not content_type:
        return ""
    mime_type = content_type.split(";", 1)[0].strip().lower()
    return mimetypes.guess_extension(mime_type) or ""


def normalize_url(url: str) -> str:
    parts = urlsplit(url)
    query = "&".join(
        f"{key}={value}" if value else key
        for key, value in sorted(parse_qsl(parts.query, keep_blank_values=True))
    )
    path = parts.path or "/"
    return urlunsplit((parts.scheme.lower(), parts.netloc.lower(), path, query, ""))


def sanitize_segment(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", value.strip())
    return cleaned[:80] or "file"


def derive_file_path(output_dir: Path, url: str, content_type: str | None) -> Path:
    parsed = urlparse(url)
    raw_path = unquote(parsed.path or "/")
    segments = [sanitize_segment(part) for part in raw_path.split("/") if part]
    if not segments:
        segments = ["root"]

    filename = segments[-1]
    stem, ext = os.path.splitext(filename)
    if not ext:
        ext = guess_extension_from_content_type(content_type)
        filename = f"{filename}{ext or '.bin'}"
        segments[-1] = filename

    if parsed.query:
        digest = hashlib.sha1(parsed.query.encode("utf-8")).hexdigest()[:10]
        stem, ext = os.path.splitext(segments[-1])
        segments[-1] = f"{stem}__{digest}{ext}"

    relative_path = Path(*segments)
    return output_dir / relative_path


def is_same_origin(base_netloc: str, candidate: str) -> bool:
    parsed = urlparse(candidate)
    return bool(parsed.scheme in {"http", "https"} and parsed.netloc == base_netloc)


def should_visit_text_resource(url: str) -> bool:
    path = urlparse(url).path
    ext = os.path.splitext(path)[1].lower()
    return ext in TEXT_RESOURCE_EXTENSIONS


def should_capture_image(url: str) -> bool:
    path = urlparse(url).path
    ext = os.path.splitext(path)[1].lower()
    return ext in IMAGE_EXTENSIONS


def strip_wrapping(value: str) -> str:
    value = value.strip().strip("\"'")
    if value.startswith("&quot;") and value.endswith("&quot;"):
        value = value[6:-6]
    return value.strip()


class ResourceHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.urls: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr_map = {name.lower(): (value or "") for name, value in attrs}
        for key in ("src", "href", "poster", "data-src", "data-original", "data-full", "content"):
            value = attr_map.get(key)
            if value:
                self.urls.add(value)

        srcset = attr_map.get("srcset")
        if srcset:
            for candidate in srcset.split(","):
                piece = candidate.strip().split(" ", 1)[0]
                if piece:
                    self.urls.add(piece)

        style = attr_map.get("style")
        if style:
            self.urls.update(extract_css_urls(style))


def extract_css_urls(text: str) -> set[str]:
    urls: set[str] = set()
    for match in CSS_URL_PATTERN.finditer(text):
        candidate = strip_wrapping(match.group("value"))
        if candidate:
            urls.add(candidate)
    return urls


def extract_urls_from_text(text: str, content_type: str | None) -> set[str]:
    urls: set[str] = set()
    parser = ResourceHTMLParser()
    lower_content_type = (content_type or "").lower()

    if "html" in lower_content_type or "<html" in text.lower():
        try:
            parser.feed(text)
            urls.update(parser.urls)
        except Exception:
            pass

    if "css" in lower_content_type or "background" in text or "url(" in text:
        urls.update(extract_css_urls(text))

    for match in URL_PATTERN.finditer(text):
        urls.add(match.group("url"))
    return urls


def absolutize_urls(base_url: str, discovered: Iterable[str]) -> set[str]:
    normalized: set[str] = set()
    for raw in discovered:
        candidate = strip_wrapping(raw)
        if not candidate:
            continue
        if candidate.startswith("//"):
            base_parts = urlparse(base_url)
            candidate = f"{base_parts.scheme}:{candidate}"
        joined = urljoin(base_url, candidate)
        parsed = urlparse(joined)
        if parsed.scheme in SKIP_SCHEMES:
            continue
        if parsed.scheme not in {"http", "https"}:
            continue
        normalized.add(joined)
    return normalized


@dataclass
class FetchResult:
    url: str
    content_type: str | None
    body: bytes


class SiteImageDownloader:
    def __init__(
        self,
        start_url: str,
        output_dir: Path,
        max_pages: int,
        max_assets: int,
        delay_ms: int,
        user_agent: str,
        cookies: str | None,
        headers_json: str | None,
    ) -> None:
        self.start_url = start_url
        self.output_dir = output_dir
        self.max_pages = max_pages
        self.max_assets = max_assets
        self.delay_ms = delay_ms
        self.base_netloc = urlparse(start_url).netloc
        self.opener = build_opener()
        self.headers = {
            "User-Agent": user_agent,
            "Accept": "*/*",
        }
        if cookies:
            self.headers["Cookie"] = cookies
        if headers_json:
            extra_headers = json.loads(headers_json)
            for key, value in extra_headers.items():
                self.headers[str(key)] = str(value)

        self.visited_text: set[str] = set()
        self.seen_images: set[str] = set()
        self.downloaded: list[Path] = []
        self.failures: list[tuple[str, str]] = []

    def fetch(self, url: str) -> FetchResult | None:
        request = Request(url, headers=self.headers)
        try:
            with self.opener.open(request, timeout=30) as response:
                body = response.read()
                content_type = response.headers.get("Content-Type")
                return FetchResult(url=response.geturl(), content_type=content_type, body=body)
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            self.failures.append((url, str(error)))
            print(f"[skip] {url} -> {error}", file=sys.stderr)
            return None

    def crawl(self) -> None:
        queue: deque[str] = deque([self.start_url])
        while queue and len(self.visited_text) < self.max_pages and len(self.seen_images) < self.max_assets:
            current = normalize_url(queue.popleft())
            if current in self.visited_text:
                continue
            if not is_same_origin(self.base_netloc, current):
                continue
            if not should_visit_text_resource(current):
                continue

            result = self.fetch(current)
            self.visited_text.add(current)
            if result is None:
                self.sleep()
                continue

            content_type = (result.content_type or "").lower()
            if not any(token in content_type for token in ("html", "css", "javascript", "json", "text")):
                self.sleep()
                continue

            try:
                text = result.body.decode("utf-8", errors="ignore")
            except Exception:
                self.sleep()
                continue

            discovered = absolutize_urls(result.url, extract_urls_from_text(text, result.content_type))
            for url in sorted(discovered):
                if not is_same_origin(self.base_netloc, url):
                    continue
                if should_capture_image(url):
                    self.download_image(url)
                elif should_visit_text_resource(url) and normalize_url(url) not in self.visited_text:
                    queue.append(url)

            self.sleep()

    def download_image(self, url: str) -> None:
        normalized = normalize_url(url)
        if normalized in self.seen_images or len(self.seen_images) >= self.max_assets:
            return
        self.seen_images.add(normalized)

        result = self.fetch(url)
        if result is None:
            self.sleep()
            return

        target = derive_file_path(self.output_dir, result.url, result.content_type)
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            base_name = target.stem
            suffix = target.suffix
            digest = hashlib.sha1(result.url.encode("utf-8")).hexdigest()[:8]
            target = target.with_name(f"{base_name}__{digest}{suffix}")

        target.write_bytes(result.body)
        self.downloaded.append(target)
        print(f"[saved] {result.url} -> {target}")
        self.sleep()

    def sleep(self) -> None:
        if self.delay_ms > 0:
            time.sleep(self.delay_ms / 1000)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Download image resources discoverable from a site URL."
    )
    parser.add_argument("url", nargs="?", help="Starting page URL.")
    parser.add_argument(
        "--output",
        default="downloads/site-images",
        help="Folder where downloaded files will be stored.",
    )
    parser.add_argument("--max-pages", type=int, default=40, help="Maximum same-origin text pages/resources to inspect.")
    parser.add_argument("--max-assets", type=int, default=500, help="Maximum image files to download.")
    parser.add_argument("--delay-ms", type=int, default=150, help="Delay between requests in milliseconds.")
    parser.add_argument(
        "--user-agent",
        default=(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/124.0.0.0 Safari/537.36"
        ),
        help="User-Agent header to send.",
    )
    parser.add_argument(
        "--cookie",
        default=None,
        help="Raw Cookie header value for authenticated sessions.",
    )
    parser.add_argument(
        "--headers-json",
        default=None,
        help='Extra headers as JSON, e.g. {"Referer":"https://example.com"}',
    )
    parser.add_argument(
        "--referer",
        default=None,
        help="Optional Referer header value.",
    )
    parser.add_argument(
        "--url-list",
        default=None,
        help="Path to a text file containing one absolute resource URL per line.",
    )
    return parser


def download_from_url_list(
    url_list_path: Path,
    output_dir: Path,
    user_agent: str,
    cookies: str | None,
    headers_json: str | None,
    referer: str | None,
) -> tuple[list[Path], list[dict[str, str]]]:
    headers = {
        "User-Agent": user_agent,
        "Accept": "*/*",
    }
    if cookies:
        headers["Cookie"] = cookies
    if referer:
        headers["Referer"] = referer
    if headers_json:
        extra_headers = json.loads(headers_json)
        for key, value in extra_headers.items():
            headers[str(key)] = str(value)

    opener = build_opener()
    downloaded: list[Path] = []
    failures: list[dict[str, str]] = []
    seen: set[str] = set()

    for raw_line in url_list_path.read_text(encoding="utf-8").splitlines():
        url = raw_line.strip()
        if not url or url.startswith("#"):
            continue
        normalized = normalize_url(url)
        if normalized in seen:
            continue
        seen.add(normalized)

        request = Request(url, headers=headers)
        try:
            with opener.open(request, timeout=30) as response:
                body = response.read()
                content_type = response.headers.get("Content-Type")
                final_url = response.geturl()
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            failures.append({"url": url, "error": str(error)})
            print(f"[skip] {url} -> {error}", file=sys.stderr)
            continue

        target = derive_file_path(output_dir, final_url, content_type)
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            base_name = target.stem
            suffix = target.suffix
            digest = hashlib.sha1(final_url.encode("utf-8")).hexdigest()[:8]
            target = target.with_name(f"{base_name}__{digest}{suffix}")

        target.write_bytes(body)
        downloaded.append(target)
        print(f"[saved] {final_url} -> {target}")

    return downloaded, failures


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    output_dir = Path(args.output).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    manifest_path = output_dir / "manifest.json"
    manifest: dict[str, object]

    if args.url_list:
        downloaded, failures = download_from_url_list(
            url_list_path=Path(args.url_list).resolve(),
            output_dir=output_dir,
            user_agent=args.user_agent,
            cookies=args.cookie,
            headers_json=args.headers_json,
            referer=args.referer,
        )
        manifest = {
            "urlList": str(Path(args.url_list).resolve()),
            "downloadedCount": len(downloaded),
            "downloads": [str(path) for path in downloaded],
            "failures": failures,
        }
        print(f"Finished. Downloaded {len(downloaded)} files from URL list.")
    else:
        if not args.url:
            parser.error("either a starting URL or --url-list is required")
        downloader = SiteImageDownloader(
            start_url=args.url,
            output_dir=output_dir,
            max_pages=args.max_pages,
            max_assets=args.max_assets,
            delay_ms=args.delay_ms,
            user_agent=args.user_agent,
            cookies=args.cookie,
            headers_json=args.headers_json,
        )
        if args.referer:
            downloader.headers["Referer"] = args.referer
        downloader.crawl()
        manifest = {
            "startUrl": args.url,
            "downloadedCount": len(downloader.downloaded),
            "visitedTextResourceCount": len(downloader.visited_text),
            "downloads": [str(path) for path in downloader.downloaded],
            "failures": [{"url": url, "error": error} for url, error in downloader.failures],
        }
        print(
            f"Finished. Downloaded {len(downloader.downloaded)} image files "
            f"after visiting {len(downloader.visited_text)} text resources."
        )

    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
