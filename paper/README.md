# Paper: Query Analyzer (ISSTA/SPLASH '26 Tool Demonstration)

Source for the 4-page tool-demonstration paper.

- `paper.tex` - the paper (ACM `acmart`, `sigconf`, self-contained TikZ figures).
- `refs.bib` - bibliography.

## Build (recommended: Overleaf)

The ACM `acmart` class is preinstalled on Overleaf, so this is the zero-setup path:

1. Create a new Overleaf project and upload `paper.tex` and `refs.bib`.
2. Menu -> Compiler -> **pdfLaTeX**.
3. Compile. Overleaf runs BibTeX automatically.

## Build (local, needs a TeX Live / MacTeX install with `acmart`)

```bash
pdflatex paper
bibtex   paper
pdflatex paper
pdflatex paper
```

If `acmart.cls` is missing: `tlmgr install acmart` (or install the full TeX Live).

## Submission checklist

- [ ] Confirm the PDF is **<= 4 pages** (references may use a 5th page). If it
      runs slightly over, trim the *Threats to validity* and *Related work*
      paragraphs first.
- [ ] Single-blind: author name/affiliation are shown (already set). Good.
- [ ] Title and abstract match what you enter in HotCRP.
- [ ] PC conflicts selected in HotCRP (Emre, Alimadadi, Hussein, Ma per the form).
- [ ] (Optional but recommended) record a 3-5 min screencast and link it in the
      Availability/Conclusion paragraph.

## Camera-ready (only if accepted)

- Remove the `nonacm` option from `\documentclass`.
- Add the official `\acmConference{...}`, `\setcopyright{...}`, `\acmDOI{...}`,
  and `\acmISBN{...}` values supplied by the proceedings chairs.

## Notes

- All numbers in the Evaluation section come from the `query-analyzer-benchmark`
  module and the `CASE_STUDY.md` in the repository root; they are reproducible
  with one command (`mvn test` in that module, after `mvn install` of the library).
- Figures are vector TikZ (no external image files), so the project is fully
  self-contained.
