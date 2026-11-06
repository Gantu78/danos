# Documento de diseño en LaTeX

## Compilar en Overleaf

1. Sube `DanosCampo-diseno-overleaf.zip` en Overleaf: **New Project → Upload Project**.
2. El compilador es **pdfLaTeX**, el que trae Overleaf por defecto, y el archivo principal es `main.tex`.
3. Recompila. Los diagramas ya van como PDF en `diagramas/`.

## Diagramas Mermaid

Las fuentes están en `diagramas/*.mmd`. Overleaf no ejecuta Mermaid, así que se incluyen los PDF ya
renderizados. Si cambias un `.mmd`, regenera su PDF desde esta carpeta:

```bash
docker run --rm -v "$PWD/diagramas:/data" minlag/mermaid-cli -c /data/config.json -i /data/02-vista-logica.mmd -o /data/02-vista-logica.pdf
```

También puedes pegar el `.mmd` en https://mermaid.live y exportarlo como PDF o SVG.

## Compilar localmente

```bash
docker run --rm -v "$PWD:/doc" -w /doc texlive/texlive:latest latexmk -pdf main.tex
```
