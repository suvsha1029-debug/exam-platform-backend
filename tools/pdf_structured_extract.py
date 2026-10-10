#!/usr/bin/env python3
"""
Structured PDF question extractor for fixed-layout exam papers.

Purpose:
- read a bordered/table-style exam PDF
- crop content-only question and option frames
- skip question-number / option-label strips
- normalize crops to legacy pre-git frame sizes
- export either viewer-clean JSON or pre-git-style JSON

Dependencies:
- pdfplumber
- pdf2image
- pillow
- numpy

Notes:
- For Windows, pdf2image usually needs Poppler installed and available on PATH,
  or provided with --poppler-path.
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

import numpy as np
import pdfplumber
from pdf2image import convert_from_path
from PIL import Image
import pypdfium2 as pdfium

NAT_RE = re.compile(r"rounded off|nearest integer|in integer|integer\)", re.I)
MSQ_RE = re.compile(r"correct option\(s\)|choose.*correct.*option", re.I)


@dataclass(frozen=True)
class PdfProfile:
    profile_id: str
    label: str
    question_regex: re.Pattern[str]
    option_regex: re.Pattern[str]
    horizontal_min_ratio: float = 0.45
    vertical_min_ratio: float = 0.03
    line_merge_gap: float = 5.0
    min_band_height: float = 15.0
    label_band_slack: float = 2.0
    band_bottom_slack: float = 5.0
    content_inset_x: float = 1.5
    content_inset_y: float = 1.5
    word_bleed_x: float = 3.0
    word_bleed_y: float = 2.0
    option_min_crop_width: float = 120.0
    trim_threshold: int = 245
    trim_bleed: int = 4


PROFILES: dict[str, PdfProfile] = {
    "gate_table_v1": PdfProfile(
        profile_id="gate_table_v1",
        label="GATE Bordered Table v1",
        question_regex=re.compile(r"^Q\.(\d+)\b"),
        option_regex=re.compile(r"^\(([A-D])\)$"),
    ),
    "question_colon_v1": PdfProfile(
        profile_id="question_colon_v1",
        label="Question Colon Sheet v1",
        question_regex=re.compile(r"^Q(?:uestion)?[\.\s:]+(\d+)\b", re.I),
        option_regex=re.compile(r"^\(?([A-D])\)?$"),
        horizontal_min_ratio=0.4,
    ),
    "compact_option_v1": PdfProfile(
        profile_id="compact_option_v1",
        label="Compact Option Block v1",
        question_regex=re.compile(r"^Q\.(\d+)\b"),
        option_regex=re.compile(r"^\(?([A-D])\)?$"),
        horizontal_min_ratio=0.4,
        trim_threshold=242,
    ),
}

LEGACY_FRAME = {
    "question": {
        "canvas_width": 640,
        "base_height": 90,
        "pad_x": 8,
        "pad_y": 6,
        "align": "top",
        "upscale": True,
    },
    "option": {
        "canvas_width": 600,
        "base_height": 55,
        "pad_x": 12,
        "pad_y": 5,
        "align": "middle",
        "upscale": False,
    },
}


def merge_nearby(values: list[float], gap: float = 5.0) -> list[float]:
    values = sorted(set(round(v, 1) for v in values))
    merged: list[float] = []
    for value in values:
        if not merged or (value - merged[-1]) > gap:
            merged.append(value)
    return merged


def get_page_structure(page, profile: PdfProfile) -> tuple[list[float], list[float]]:
    page_height = page.height
    page_width = page.width
    rects = page.rects

    h_lines = []
    for rect in rects:
        rect_w = rect["x1"] - rect["x0"]
        rect_h = rect["y1"] - rect["y0"]
        if rect_h < 3 and rect_w > page_width * profile.horizontal_min_ratio:
            h_lines.append(rect["top"])

    v_lines = []
    for rect in rects:
        rect_w = rect["x1"] - rect["x0"]
        rect_h = rect["y1"] - rect["y0"]
        if rect_w < 3 and rect_h > page_height * profile.vertical_min_ratio:
            v_lines.append(rect["x0"])

    return merge_nearby(h_lines, profile.line_merge_gap), merge_nearby(v_lines, profile.line_merge_gap)


def classify_band(col1_words: list[str], profile: PdfProfile) -> tuple[str, int | str | None]:
    left_text = " ".join(col1_words).strip()
    question_match = profile.question_regex.match(left_text)
    if question_match:
        return "question", int(question_match.group(1))

    option_match = profile.option_regex.match(left_text)
    if option_match:
        return "option", option_match.group(1)

    if not left_text:
        return "spacer", None

    return "other", None


def crop_px(img_arr: np.ndarray, dpi_scale: float, y_top: float, y_bottom: float, x_left: float, x_right: float) -> np.ndarray:
    img_h, img_w = img_arr.shape[:2]
    px0 = max(0, int(x_left * dpi_scale))
    py0 = max(0, int(y_top * dpi_scale))
    px1 = min(img_w, int(x_right * dpi_scale))
    py1 = min(img_h, int(y_bottom * dpi_scale))
    return img_arr[py0:py1, px0:px1]


def clamp_crop_width(
    x_left: float,
    x_right: float,
    *,
    min_width: float,
    hard_left: float,
    hard_right: float,
) -> tuple[float, float]:
    width = x_right - x_left
    if width >= min_width:
        return x_left, x_right
    center = (x_left + x_right) / 2.0
    half = min_width / 2.0
    new_left = max(hard_left, center - half)
    new_right = min(hard_right, center + half)
    if (new_right - new_left) < min_width:
        if new_left <= hard_left:
            new_right = min(hard_right, new_left + min_width)
        elif new_right >= hard_right:
            new_left = max(hard_left, new_right - min_width)
    return new_left, new_right


def resolve_crop_bounds(
    *,
    role: str,
    content_words: list[dict],
    band_top: float,
    band_bottom: float,
    content_left: float,
    content_right: float,
    profile: PdfProfile,
) -> tuple[float, float, float, float]:
    crop_top = band_top + profile.content_inset_y
    crop_bottom = band_bottom - profile.content_inset_y
    crop_left = content_left
    crop_right = content_right

    if not content_words:
        return crop_top, crop_bottom, crop_left, crop_right

    min_x0 = min(word["x0"] for word in content_words)
    max_x1 = max(word["x1"] for word in content_words)
    min_top = min(word["top"] for word in content_words)
    max_bottom = max(word["bottom"] for word in content_words)

    crop_top = max(crop_top, min_top - profile.word_bleed_y)
    crop_bottom = min(crop_bottom, max_bottom + profile.word_bleed_y)

    if role == "option":
        crop_left = max(content_left, min_x0 - profile.word_bleed_x)
        crop_right = min(content_right, max_x1 + profile.word_bleed_x)
        crop_left, crop_right = clamp_crop_width(
            crop_left,
            crop_right,
            min_width=profile.option_min_crop_width,
            hard_left=content_left,
            hard_right=content_right,
        )

    return crop_top, crop_bottom, crop_left, crop_right


def band_words(
    words: list[dict],
    *,
    y_top: float,
    y_bottom: float,
    x_min: float | None = None,
    x_max: float | None = None,
) -> list[dict]:
    selected: list[dict] = []
    for word in words:
        if word["top"] < y_top or word["bottom"] > y_bottom + 5:
            continue
        if x_min is not None and word["x0"] < x_min:
            continue
        if x_max is not None and word["x0"] >= x_max:
            continue
        selected.append(word)
    return selected


def detect_profile(pdf_path: Path) -> PdfProfile:
    with pdfplumber.open(str(pdf_path)) as pdf:
        sample_pages = pdf.pages[: min(5, len(pdf.pages))]
        sample_text = "\n".join((page.extract_text() or "") for page in sample_pages)
        sample_rect_count = sum(len(page.rects) for page in sample_pages)

    # Start with the bordered GATE-style profile when the geometry and question syntax match.
    if "Q." in sample_text and sample_rect_count >= 20:
        return PROFILES["gate_table_v1"]
    if re.search(r"Q(?:uestion)?[\.\s:]+\d+", sample_text, re.I):
        return PROFILES["question_colon_v1"]
    return PROFILES["compact_option_v1"]


def trim_whitespace(img_arr: np.ndarray, threshold: int = 245, bleed: int = 4) -> np.ndarray:
    if img_arr.size == 0:
        return img_arr

    if img_arr.ndim == 2:
        mask = img_arr < threshold
    else:
        rgb = img_arr[:, :, :3]
        mask = np.any(rgb < threshold, axis=2)

    coords = np.argwhere(mask)
    if coords.size == 0:
        return img_arr

    y0, x0 = coords.min(axis=0)
    y1, x1 = coords.max(axis=0)
    y0 = max(0, y0 - bleed)
    x0 = max(0, x0 - bleed)
    y1 = min(img_arr.shape[0] - 1, y1 + bleed)
    x1 = min(img_arr.shape[1] - 1, x1 + bleed)
    return img_arr[y0 : y1 + 1, x0 : x1 + 1]


def normalize_legacy_frame(img_arr: np.ndarray, frame_kind: str) -> np.ndarray:
    spec = LEGACY_FRAME[frame_kind]
    source = Image.fromarray(img_arr)
    target_width = spec["canvas_width"]
    usable_width = max(32, target_width - (spec["pad_x"] * 2))
    raw_scale = usable_width / max(1, source.width)
    scale = raw_scale if spec.get("upscale", True) else min(1.0, raw_scale)
    draw_width = max(1, round(source.width * scale))
    draw_height = max(1, round(source.height * scale))
    target_height = max(spec["base_height"], draw_height + (spec["pad_y"] * 2))

    canvas = Image.new("RGB", (target_width, target_height), "white")
    resized = source.resize((draw_width, draw_height), Image.Resampling.LANCZOS)
    draw_x = max(0, (target_width - draw_width) // 2)
    if spec["align"] == "middle":
        draw_y = max(0, (target_height - draw_height) // 2)
    else:
        draw_y = max(0, spec["pad_y"])
    canvas.paste(resized, (draw_x, draw_y))
    return np.array(canvas)


def to_b64(img_arr: np.ndarray) -> str:
    image = Image.fromarray(img_arr)
    buf = io.BytesIO()
    image.save(buf, format="PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode("utf-8")


def to_data_url(b64: str) -> str:
    return f"data:image/png;base64,{b64}" if b64 else ""


def build_viewer_question(q_num: int, q_type: str, question_b64: str) -> dict:
    return {
        "q_id": f"Q{q_num}",
        "q_num": q_num,
        "type": q_type,
        "question_frame": {"file": f"Q{q_num:03d}_question.png", "b64": question_b64},
        "options": [],
    }


def render_pages(pdf_path: Path, dpi: int, poppler_path: str = "") -> list[Image.Image]:
    if poppler_path:
        return convert_from_path(str(pdf_path), dpi=dpi, poppler_path=poppler_path)

    try:
        return convert_from_path(str(pdf_path), dpi=dpi)
    except Exception:
        scale = dpi / 72.0
        pdf_doc = pdfium.PdfDocument(str(pdf_path))
        pages: list[Image.Image] = []
        for index in range(len(pdf_doc)):
            page = pdf_doc[index]
            bitmap = page.render(scale=scale)
            pil_image = bitmap.to_pil()
            pages.append(pil_image.convert("RGB"))
        return pages


def to_pre_git_question(question: dict, subject: str) -> dict:
    question_b64 = question["question_frame"]["b64"]
    q_data_url = to_data_url(question_b64)
    options = []
    correct_option_ids = []
    for option in question["options"]:
        option_b64 = option["frame"]["b64"]
        data_url = to_data_url(option_b64)
        options.append(
            {
                "oid": option["opt_id"],
                "image": data_url,
                "baseImage": data_url,
                "viewerImage": data_url,
                "text": "",
                "composerHTML": "",
                "figures": [],
                "legends": [],
                "correct": False,
            }
        )
    return {
        "id": f"{subject}{question['q_num']:03d}",
        "qid": question["q_id"],
        "q_num": question["q_num"],
        "type": question["type"],
        "paperProfileId": question.get("paperProfileId", ""),
        "subject": subject,
        "label": "",
        "marks": 2,
        "negMarks": -0.67,
        "questionImage": q_data_url,
        "questionBaseImage": q_data_url,
        "questionViewerImage": q_data_url,
        "questionText": "",
        "questionComposerHTML": "",
        "questionFigures": [],
        "questionLegends": [],
        "natAnswer": "" if question["type"] == "NAT" else None,
        "correctOptionIds": correct_option_ids,
        "options": [] if question["type"] == "NAT" else options,
    }


def build_pre_git_payload(questions: list[dict], subject: str, exam_name: str) -> dict:
    saved_at = datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    return {
        "_version": 3,
        "_app": "QuestionBankGenerator",
        "_savedAt": saved_at,
        "examName": exam_name,
        "subjects": [
            {
                "short": subject,
                "full": subject,
                "section": subject,
            }
        ],
        "qs": [to_pre_git_question(question, subject) for question in questions],
    }


def extract(
    pdf_path: Path,
    *,
    dpi: int = 200,
    profile: PdfProfile,
    poppler_path: str = "",
    progress_cb: Callable[[str], None] | None = None,
) -> list[dict]:
    dpi_scale = dpi / 72.0
    with pdfplumber.open(str(pdf_path)) as pdf:
        page_images = render_pages(pdf_path, dpi=dpi, poppler_path=poppler_path)
        questions: dict[int, dict] = {}
        current_q_num: int | None = None
        msq_candidates: set[int] = set()

        if progress_cb:
            progress_cb(f"Converting {len(pdf.pages)} pages...")

        for page_index, page in enumerate(pdf.pages):
            h_lines, v_lines = get_page_structure(page, profile)
            if len(h_lines) < 2 or len(v_lines) < 2:
                continue

            x_label_left = v_lines[0]
            x_content_left = v_lines[1]
            x_right = v_lines[-1]
            content_left = x_content_left + profile.content_inset_x
            content_right = x_right - profile.content_inset_x

            img_arr = np.array(page_images[page_index])
            words = page.extract_words(x_tolerance=3, y_tolerance=3)
            page_text = page.extract_text() or ""
            if MSQ_RE.search(page_text):
                for match in re.findall(r"Q\.(\d+)", page_text):
                    msq_candidates.add(int(match))

            for band_index in range(len(h_lines) - 1):
                y_top = h_lines[band_index]
                y_bottom = h_lines[band_index + 1]
                if (y_bottom - y_top) < profile.min_band_height:
                    continue

                label_word_items = band_words(
                    words,
                    y_top=y_top,
                    y_bottom=y_bottom,
                    x_min=x_label_left,
                    x_max=x_content_left + profile.label_band_slack,
                )
                label_words = [word["text"] for word in label_word_items]
                content_word_items = band_words(
                    words,
                    y_top=y_top,
                    y_bottom=y_bottom,
                    x_min=content_left,
                )
                content_words = [word["text"] for word in content_word_items]
                role, meta = classify_band(label_words, profile)
                if role in {"spacer", "other"}:
                    continue

                band_text = " ".join(label_words + content_words)
                crop_top, crop_bottom, crop_left, crop_right = resolve_crop_bounds(
                    role=role,
                    content_words=content_word_items,
                    band_top=y_top,
                    band_bottom=y_bottom,
                    content_left=content_left,
                    content_right=content_right,
                    profile=profile,
                )
                crop = crop_px(
                    img_arr,
                    dpi_scale,
                    crop_top,
                    crop_bottom,
                    crop_left,
                    crop_right,
                )
                crop = trim_whitespace(crop, threshold=profile.trim_threshold, bleed=profile.trim_bleed)
                frame_kind = "question" if role == "question" else "option"
                crop = normalize_legacy_frame(crop, frame_kind)
                crop_b64 = to_b64(crop)

                if role == "question":
                    current_q_num = int(meta)
                    question_type = "NAT" if NAT_RE.search(band_text) else "MCQ"
                    questions[current_q_num] = build_viewer_question(current_q_num, question_type, crop_b64)
                    questions[current_q_num]["paperProfileId"] = profile.profile_id
                elif role == "option" and current_q_num is not None:
                    label = str(meta)
                    questions[current_q_num]["options"].append(
                        {
                            "opt_id": f"Q{current_q_num}_opt{label}",
                            "label": label,
                            "frame": {"file": f"Q{current_q_num:03d}_opt{label}.png", "b64": crop_b64},
                        }
                    )
                    questions[current_q_num]["type"] = "MCQ"

            if progress_cb:
                progress_cb(f"Page {page_index + 1}/{len(pdf.pages)} done")

        result = [questions[key] for key in sorted(questions.keys())]
        for question in result:
            if not question["options"]:
                question["type"] = "NAT"
            elif question["q_num"] in msq_candidates:
                question["type"] = "MSQ"
        if progress_cb:
            progress_cb(f"Done - {len(result)} questions extracted")
        return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract structured question frames from a fixed-layout PDF.")
    parser.add_argument("--pdf", required=True, help="Path to input PDF")
    parser.add_argument("--out", required=True, help="Path to output JSON")
    parser.add_argument("--format", choices=["viewer", "pre-git"], default="viewer", help="Output schema")
    parser.add_argument("--profile", default="auto", help="Profile id or 'auto'")
    parser.add_argument("--list-profiles", action="store_true", help="List available profiles and exit")
    parser.add_argument("--dpi", type=int, default=200, help="Rasterization DPI")
    parser.add_argument("--subject", default="GEN", help="Subject code for pre-git export")
    parser.add_argument("--poppler-path", default="", help="Optional Poppler bin path for Windows")
    args = parser.parse_args()

    if args.list_profiles:
        print("Available profiles:")
        for profile in PROFILES.values():
            print(f"- {profile.profile_id}: {profile.label}")
        return 0

    pdf_path = Path(args.pdf).expanduser().resolve()
    out_path = Path(args.out).expanduser().resolve()
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    kwargs = {"dpi": args.dpi, "poppler_path": args.poppler_path}

    if args.profile == "auto":
        profile = detect_profile(pdf_path)
    else:
        if args.profile not in PROFILES:
            raise ValueError(f"Unknown profile: {args.profile}")
        profile = PROFILES[args.profile]

    print(f"Profile: {profile.profile_id} ({profile.label})")
    questions = extract(pdf_path, profile=profile, progress_cb=print, **kwargs)
    if args.format == "pre-git":
        payload = build_pre_git_payload(questions, args.subject, pdf_path.stem)
    else:
        payload = questions

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")

    mcq_count = sum(1 for q in questions if q["type"] == "MCQ")
    nat_count = sum(1 for q in questions if q["type"] == "NAT")
    msq_count = sum(1 for q in questions if q["type"] == "MSQ")
    print(f"MCQ={mcq_count} NAT={nat_count} MSQ={msq_count} Total={len(questions)}")
    print(f"Saved: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
