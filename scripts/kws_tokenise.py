#!/usr/bin/env python3
"""Tokenise a wake phrase for the sherpa-onnx keyword spotter.

The spotter does not take plain text — keywords are BPE token sequences in the model's
own vocabulary, with U+2581 marking a word start:

    HELLO WORLD  ->  ▁HE LL O ▁WORLD

Kotlin can only approximate that with greedy longest-match (see KwsKeywords.tokenise),
so anything that needs to detect reliably should be tokenised here with the model's real
bpe.model and pasted into KwsKeywords.EXACT.

Usage:
    pip install sentencepiece
    python3 scripts/kws_tokenise.py <model-dir> "hey meow" ["hey memba" ...]

<model-dir> is the extracted KWS model containing bpe.model and tokens.txt, e.g.
sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01 (the app assets copy drops
bpe.model, so point this at the downloaded tarball).

Self-check: with no phrases given, it re-tokenises the phrases from the model's own
keywords_raw.txt and diffs against its keywords.txt. Any mismatch means this script and
the model disagree and the output must not be trusted.
"""
from __future__ import annotations

import os
import sys


def load(model_dir: str):
    try:
        import sentencepiece as spm
    except ImportError:
        sys.exit("sentencepiece is required:  pip install sentencepiece")

    bpe = os.path.join(model_dir, "bpe.model")
    tokens = os.path.join(model_dir, "tokens.txt")
    for p in (bpe, tokens):
        if not os.path.isfile(p):
            sys.exit(f"missing {p}")

    sp = spm.SentencePieceProcessor()
    sp.load(bpe)

    vocab = set()
    with open(tokens, encoding="utf-8") as fh:
        for line in fh:
            cut = line.rstrip("\n").rfind(" ")
            if cut > 0:
                vocab.add(line[:cut])
    return sp, vocab


def tokenise(sp, vocab, phrase: str) -> str:
    toks = sp.encode(phrase.upper(), out_type=str)
    missing = [t for t in toks if t not in vocab]
    if missing:
        sys.exit(f"{phrase!r}: tokens absent from the model vocabulary: {missing}")
    return " ".join(toks)


def self_check(sp, vocab, model_dir: str) -> int:
    raw = os.path.join(model_dir, "keywords_raw.txt")
    want = os.path.join(model_dir, "keywords.txt")
    if not (os.path.isfile(raw) and os.path.isfile(want)):
        print("no keywords_raw.txt/keywords.txt to check against; skipping")
        return 0

    with open(raw, encoding="utf-8") as fh:
        phrases = [l.strip() for l in fh if l.strip()]
    with open(want, encoding="utf-8") as fh:
        expected = [l.strip() for l in fh if l.strip()]

    bad = 0
    for phrase, exp in zip(phrases, expected):
        got = " ".join(sp.encode(phrase.upper(), out_type=str))
        flag = "ok " if got == exp else "DIFF"
        if got != exp:
            bad += 1
            print(f"{flag} {phrase!r}\n     expected: {exp}\n     got:      {got}")
    print(f"self-check: {len(phrases) - bad}/{len(phrases)} phrases match the model's own keywords.txt")
    return bad


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    model_dir = sys.argv[1]
    phrases = sys.argv[2:]
    sp, vocab = load(model_dir)

    if not phrases:
        sys.exit(1 if self_check(sp, vocab, model_dir) else 0)

    print("// paste into KwsKeywords.EXACT")
    for p in phrases:
        print(f'        "{p.lower()}" to "{tokenise(sp, vocab, p)}",')
