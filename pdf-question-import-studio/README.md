# PDF Question Import Studio

Standalone structured-PDF importer for exam question papers.

Purpose:
- Keep PDF ingestion separate from the manual question generator.
- Support only strict, text-selectable PDF layouts.
- Parse `Q.xx` question legends and `(A) (B) (C) (D)` style options.
- Show a review step before export into the exam JSON schema.

Current status:
- Standalone UI scaffold
- Strict workflow layout
- Local review and sample parsing shell
- No backend dependency required for the first UI pass

Recommended workflow:
1. Open `index.html`
2. Upload a born-digital PDF or paste extracted text
3. Define strict parsing format
4. Review split questions and options
5. Export JSON only after manual verification

Important:
- This module is intentionally separate from the live question generator.
- First release should support only exact templates, not arbitrary PDFs.
