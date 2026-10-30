"""Convertit bank_transactions.pdf (tableau imprimé, colonnes réparties sur des pages alternées)
en bank_transactions.csv. Les valeurs manquantes (cellules vides) sont conservées vides.

Usage :  python pdf_to_csv.py bank_transactions.pdf bank_transactions.csv
"""
import csv
import sys
from collections import defaultdict

import pdfplumber

LEFT = ["transaction_id", "account_id", "amount", "hour", "country", "merchant_category"]
RIGHT = ["transactions_last_24h", "average_amount_last_7d", "is_fraud"]


def page_rows(page, columns):
    """Regroupe les mots par ligne (y) puis les affecte à la colonne dont l'en-tête est la plus proche."""
    words = page.extract_words(keep_blank_chars=False)
    header = {}
    for w in words:
        if w["text"] in columns:
            header[w["text"]] = (w["x0"] + w["x1"]) / 2
    if len(header) != len(columns):
        return None
    header_y = max(w["bottom"] for w in words if w["text"] in columns)
    rows = defaultdict(dict)
    for w in words:
        if w["top"] <= header_y or w["text"] in columns:
            continue
        centre = (w["x0"] + w["x1"]) / 2
        column = min(header, key=lambda c: abs(header[c] - centre))
        rows[round(w["top"])][column] = w["text"]
    return [rows[y] for y in sorted(rows)]


def main(src, dst):
    left, right = [], []
    with pdfplumber.open(src) as pdf:
        for page in pdf.pages:
            for columns, target in ((LEFT, left), (RIGHT, right)):
                rows = page_rows(page, columns)
                if rows is not None:
                    target.extend(rows)
                    break
    if len(left) != len(right):
        raise SystemExit(f"Lignes incohérentes : {len(left)} (gauche) / {len(right)} (droite)")
    with open(dst, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=LEFT + RIGHT)
        writer.writeheader()
        for a, b in zip(left, right):
            writer.writerow({**a, **b})
    print(f"{len(left)} lignes écrites dans {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
