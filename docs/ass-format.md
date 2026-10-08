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
calculada a partir de uma **grade de caracteres** (`AssLayout`): assume-se que
cada caractere de largura total (kanji, kana, pontuação japonesa) ocupa um
quadrado igual ao tamanho da fonte, e que caracteres ASCII ocupam meio quadrado.
Isso mantém o alinhamento preciso para textos japoneses; para trechos em latim
misturados, é uma aproximação.

O texto e a sincronização originais são sempre preservados. A associação entre
segmentos e linhas visuais é feita por deslocamento de caractere no texto
original, o que funciona tanto com tokenizadores que preservam quebras de linha
como tokens (Kuromoji) quanto com os que as descartam
(`ScriptJapaneseTokenizer`).

As constantes geométricas (`AssLayout`) pressupõem a resolução 1920x1080 do
template padrão; alterar o `PlayRes` do template exige ajustar essas constantes.

## Estilos

Os estilos ASS são mantidos separados da lógica de geração: ficam no recurso
`src/main/resources/ass/default-style.ass`, que também define `[Script Info]`,
resolução e o cabeçalho de `[Events]`. O `AssGenerator` apenas anexa os eventos.

Estilos atuais:

```text
Main       — texto principal (branco, 64px)
Furigana   — leitura (dourado, 32px), exibida acima do texto principal
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

- caracteres com largura muito variável (ex.: trechos longos em latim);
- colisão entre linhas com furigana em textos com muitas linhas;
- fontes japonesas específicas e métricas reais de glifos;
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
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(608,928)}きょう
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(832,928)}にほんご
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(1056,928)}べんきょう
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
