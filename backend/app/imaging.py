"""Úprava fotky dokladu pro OCR (OpenCV).

Mobilní aplikace posílá už upravený sken (ML Kit / VisionKit). Tento modul slouží
pro doklady nahrané bez úpravy (web, galerie, e-mail) a jako záloha:

1. otočení podle EXIF
2. nalezení okrajů papíru a srovnání perspektivy (ořez do obdélníku)
3. vyrovnání osvětlení – odstranění stínů a přechodů (dělení odhadnutým pozadím)
4. zvýšení kontrastu (CLAHE) a lehké doostření
5. režim „bw“ = adaptivní prahování (čistě černobílý sken)
"""
from __future__ import annotations

import io

import cv2
import numpy as np
from PIL import Image, ImageOps

MAX_SIDE = 2600


def _load(data: bytes) -> np.ndarray:
    img = Image.open(io.BytesIO(data))
    img = ImageOps.exif_transpose(img).convert("RGB")
    arr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    h, w = arr.shape[:2]
    scale = MAX_SIDE / max(h, w)
    if scale < 1:
        arr = cv2.resize(arr, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    return arr


def _order_points(pts: np.ndarray) -> np.ndarray:
    pts = pts.reshape(4, 2).astype("float32")
    s = pts.sum(axis=1)
    d = np.diff(pts, axis=1).ravel()
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)], pts[np.argmax(s)], pts[np.argmax(d)]], dtype="float32")


def find_document_quad(img: np.ndarray) -> np.ndarray | None:
    """Vrátí 4 rohy papíru (v souřadnicích img) nebo None."""
    h, w = img.shape[:2]
    scale = 800 / max(h, w)
    small = cv2.resize(img, (int(w * scale), int(h * scale))) if scale < 1 else img.copy()
    scale = min(scale, 1.0)
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    # zavření odstraní text uvnitř papíru, aby nerušil hledání okrajů
    gray = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8), iterations=2)
    edges = cv2.Canny(gray, 40, 140)
    edges = cv2.dilate(edges, np.ones((3, 3), np.uint8), iterations=2)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    area_min = 0.2 * small.shape[0] * small.shape[1]
    for c in sorted(contours, key=cv2.contourArea, reverse=True)[:8]:
        if cv2.contourArea(c) < area_min:
            break
        peri = cv2.arcLength(c, True)
        for eps in (0.02, 0.03, 0.05):
            approx = cv2.approxPolyDP(c, eps * peri, True)
            if len(approx) == 4 and cv2.isContourConvex(approx):
                return _order_points(approx) / scale
    return None


def warp(img: np.ndarray, quad: np.ndarray) -> np.ndarray:
    tl, tr, br, bl = quad
    width = int(max(np.linalg.norm(br - bl), np.linalg.norm(tr - tl)))
    height = int(max(np.linalg.norm(tr - br), np.linalg.norm(tl - bl)))
    dst = np.array([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]], dtype="float32")
    m = cv2.getPerspectiveTransform(quad.astype("float32"), dst)
    return cv2.warpPerspective(img, m, (width, height), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


def normalize_illumination(gray: np.ndarray) -> np.ndarray:
    """Odhadne pozadí papíru (velké rozmazání po dilataci) a vydělí jím obraz → zmizí stíny."""
    k = max(15, (min(gray.shape) // 25) | 1)
    bg = cv2.dilate(gray, np.ones((7, 7), np.uint8))
    bg = cv2.medianBlur(bg, k if k < 255 else 255)
    norm = cv2.divide(gray, bg, scale=255)
    return cv2.normalize(norm, None, 0, 255, cv2.NORM_MINMAX)


def deskew(gray: np.ndarray) -> tuple[np.ndarray, float]:
    """Dorovná drobné natočení textu (±10°), když se nenašly okraje papíru."""
    inv = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[1]
    lines = cv2.HoughLinesP(inv, 1, np.pi / 180, 200, minLineLength=gray.shape[1] // 4, maxLineGap=20)
    if lines is None:
        return gray, 0.0
    angles = []
    for x1, y1, x2, y2 in lines[:, 0]:
        a = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        if abs(a) < 10:
            angles.append(a)
    if not angles:
        return gray, 0.0
    angle = float(np.median(angles))
    if abs(angle) < 0.3:
        return gray, 0.0
    h, w = gray.shape
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(gray, m, (w, h), flags=cv2.INTER_CUBIC, borderValue=255), angle


def enhance(data: bytes, mode: str = "gray") -> tuple[bytes, dict]:
    """mode: 'gray' (doporučeno pro OCR), 'bw' (černobílý sken), 'color' (jen ořez + světlo)."""
    img = _load(data)
    info: dict = {"mode": mode, "input_size": list(img.shape[1::-1])}

    quad = find_document_quad(img)
    if quad is not None:
        img = warp(img, quad)
        info["cropped"] = True
        info["corners"] = quad.round(1).tolist()
    else:
        info["cropped"] = False

    if mode == "color":
        lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        l = normalize_illumination(l)
        l = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(l)
        out = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
    else:
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        if not info["cropped"]:
            gray, angle = deskew(gray)
            info["deskew_deg"] = round(angle, 2)
        gray = normalize_illumination(gray)
        gray = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8)).apply(gray)
        blur = cv2.GaussianBlur(gray, (0, 0), 1.0)
        gray = cv2.addWeighted(gray, 1.5, blur, -0.5, 0)  # unsharp mask
        if mode == "bw":
            gray = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 15)
        out = gray

    info["output_size"] = list(out.shape[1::-1])
    ok, buf = cv2.imencode(".jpg", out, [cv2.IMWRITE_JPEG_QUALITY, 90])
    if not ok:
        raise ValueError("Nepodařilo se uložit upravený obrázek")
    return buf.tobytes(), info
