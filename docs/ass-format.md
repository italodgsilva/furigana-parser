# Formato ASS e Furigana

## Objetivo

O formato ASS (Advanced SubStation Alpha) é utilizado para produzir legendas com
maior controle de posicionamento e formatação.

O objetivo é representar visualmente algo semelhante a:

```text
   きょう       にほんご
今日は日本語を勉強します。
```

em vez de simplesmente:

```text
今日は日本語を勉強します。
（きょう）（にほんご）
```

## Por que ASS?

SRT possui uma estrutura simples e pouca capacidade de controle visual.

ASS permite controlar propriedades como:

- posição;
- fonte;
- tamanho;
- cor;
- alinhamento;
- estilo;
- espaçamento;
- efeitos de texto;
- posicionamento individual de elementos.

Isso o torna mais adequado para uma representação de furigana.

## Importante

ASS não deve ser tratado como parte da lógica de obtenção das leituras.

A arquitetura é:

```text
Japonês
   ↓
Tokenização
   ↓
Leituras
   ↓
FuriganaDocument
   ↓
AssGenerator
   ↓
ASS
```

O `AssGenerator` recebe um documento já processado. Ele não consulta o Jisho nem
conhece SRT ou tokenizadores.

## Modelo visual

Considere:

```text
日本語
にほんご
```

A representação desejada é:

```text
にほんご
日本語
```

com a leitura posicionada acima do texto principal.

Para uma frase:

```text
今日は日本語を勉強します。
```

podemos conceitualmente dividir a linha em:

```text
今日 | は | 日本語 | を | 勉強 | します | 。
```

e renderizar apenas os segmentos que possuem leitura:

```text
  きょう             にほんご       べんきょう
今日は              日本語        勉強 します。
```

## Estratégias possíveis

### Estratégia 1 — duas linhas

Gerar duas linhas de texto:

```text
furigana
texto principal
```

É relativamente simples, mas apresenta problemas de alinhamento quando existem
vários segmentos com tamanhos diferentes.

### Estratégia 2 — posicionamento por segmento

Cada segmento é renderizado separadamente:

```text
[今日] [は] [日本語] [を] [勉強] [します]
  ↑             ↑            ↑
furigana      furigana     furigana
```

Oferece maior controle, mas aumenta a complexidade do gerador.

### Estratégia 3 — tags e posicionamento ASS

Utiliza tags de posicionamento e estilos ASS para construir a composição
diretamente dentro da legenda.

## Estratégia adotada

Foi adotada a **Estratégia 3**, combinada com a ideia da Estratégia 2:
posicionamento por segmento via tags ASS.

O `AssGenerator`:

- emite um evento por **linha visual** do texto principal, no estilo `Main`,
  centralizado com `{\an5\pos(x,y)}`;
- emite um evento independente de furigana, no estilo `Furigana`, para cada
  segmento com leitura, posicionado com `{\an5\pos(x,y)}` sobre o kanji
  correspondente.

Como o ASS não possui rubi nativo, a coordenada horizontal do furigana é
calculada a partir de uma **grade de caracteres** (`AssLayout`): cada caractere de
largura total (kanji, kana, pontuação japonesa) ocupa um "em", o katakana
half-width ocupa meio "em", e os caracteres ASCII usam o **avanço real** da fonte
do template (`ASCII_ADVANCES`, extraído de `hmtx`). O latim de fonte CJK é
proporcional — o espaço mede `0,224` em, o `(` mede `0,338` em —, bem distante de
uma largura fixa de meio "em", e ignorar isso desloca o furigana de forma
cumulativa ao longo da linha.

O "em" **não** é o tamanho de fonte declarado no ASS: o libass (usado pelo MPV)
interpreta o `Fontsize` como a altura da linha da fonte (`hhea`), então o avanço
real de um caractere de largura total é `Fontsize * EM_PER_FONT_SIZE`. Para a
fonte do template (`Noto Sans CJK JP`), `EM_PER_FONT_SIZE = 1000/1448 ≈ 0,6906`.
As coordenadas verticais do texto principal usam a caixa de linha do libass
(que tem exatamente a altura do `Fontsize`); o furigana é posicionado pelos
"em" (as caixas de glifo, `CELL_WIDTH` e `FURIGANA_FONT_SIZE * EM_PER_FONT_SIZE`),
com uma folga pequena (`FURIGANA_GAP`) — a caixa de linha desta fonte tem um
"leading" interno grande, que afastaria a leitura do kanji se fosse usada como
referência.

O texto e a sincronização originais são sempre preservados. A associação entre
segmentos e linhas visuais é feita por deslocamento de caractere no texto
original, o que funciona tanto com tokenizadores que preservam quebras de linha
como tokens (Kuromoji) quanto com os que as descartam
(`ScriptJapaneseTokenizer`).

As constantes geométricas (`AssLayout`) pressupõem a resolução 1920x1080 e a
fonte do template padrão; alterar o `PlayRes` ou a fonte exige ajustar essas
constantes (em particular `EM_PER_FONT_SIZE` e a tabela `ASCII_ADVANCES`).

## Estilos

Os estilos ASS são mantidos separados da lógica de geração: ficam no recurso
`src/main/resources/ass/default-style.ass`, que também define `[Script Info]`,
resolução e o cabeçalho de `[Events]`. O `AssGenerator` apenas anexa os eventos.

Estilos atuais:

```text
Main       — texto principal (branco, 64px)
Furigana   — leitura (branco, 32px), exibida acima do texto principal
```

Isso permite alterar fonte, tamanho, cor, margem e estilo sem modificar a lógica
responsável pela composição do texto.

## Sincronização

O timestamp da legenda original é preservado.

Se uma entrada SRT tiver:

```text
00:01:12,500 --> 00:01:15,200
```

a entrada ASS correspondente utiliza o mesmo intervalo temporal
(`0:01:12.50` → `0:01:15.20`). O processo de adicionar furigana não altera a
sincronização.

## Caracteres japoneses

O gerador preserva integralmente:

- kanji;
- hiragana;
- katakana;
- pontuação japonesa;
- espaços relevantes;
- caracteres Unicode presentes na legenda.

A codificação de saída é UTF-8.

## Questões em aberto

Pontos que ainda podem exigir refinamento:

- caracteres fora de ASCII e CJK (ex.: letras latinas acentuadas, outros
  alfabetos) ainda usam um "em" por padrão, sem medição real;
- kerning entre pares de glifos não é considerado;
- colisão entre linhas com furigana em textos com muitas linhas;
- textos muito longos (quebra automática).

## Exemplo conceitual de transformação

Entrada:

```text
今日は日本語を勉強します。
```

Modelo intermediário:

```text
今日       → きょう
は         → -
日本語     → にほんご
を         → -
勉強       → べんきょう
します     → -
。         → -
```

Saída ASS real (trecho):

```text
Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,{\an5\pos(960,988)}今日は日本語を勉強します。
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(717,949)}きょう
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(872,949)}にほんご
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(1026,949)}べんきょう
```

## Relação com MPV

O MPV será responsável apenas pela reprodução e exibição do arquivo ASS.

O pipeline de geração é independente:

```text
SRT
 ↓
Furigana Parser
 ↓
ASS
 ↓
MPV
```

Posteriormente poderá ser criada uma integração mais dinâmica, na qual o MPV
utilize scripts para solicitar ou atualizar as leituras durante a reprodução.

Essa integração fica fora do escopo inicial.
