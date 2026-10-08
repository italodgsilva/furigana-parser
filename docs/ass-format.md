# Formato ASS e Furigana

## Objetivo

O formato ASS (Advanced SubStation Alpha) será utilizado posteriormente para produzir legendas com maior controle de posicionamento e formatação.

O objetivo é conseguir representar visualmente algo semelhante a:

```text
   きょう       にほんご
今日は日本語を勉強します。
````

 em vez de simplesmente:

```
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

 A arquitetura será:

```
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

 O `AssGenerator` recebe um documento já processado.

 Ele não deve consultar o Jisho.

 ## Modelo visual

 Considere:

```
日本語
にほんご
```

 A representação desejada é:

```
にほんご
日本語
```

 com a leitura posicionada acima do texto principal.

 Para uma frase:

```
今日は日本語を勉強します。
```

 podemos conceitualmente dividir a linha em:

```
今日 | は | 日本語 | を | 勉強 | します | 。
```

 e renderizar apenas os segmentos que possuem leitura:

```
  きょう             にほんご       べんきょう
今日は              日本語        勉強 します。
```

 O posicionamento exato dependerá da implementação do gerador.

 ## Estratégias possíveis

 Ainda não foi definida uma estratégia final.

 ### Estratégia 1 — duas linhas

 Uma possibilidade é gerar duas linhas de texto:

```
furigana
texto principal
```

 Isso é relativamente simples, mas apresenta problemas de alinhamento quando existem vários segmentos com tamanhos diferentes.

 ### Estratégia 2 — posicionamento por segmento

 Cada segmento pode ser renderizado separadamente.

 Conceitualmente:

```
[今日] [は] [日本語] [を] [勉強] [します]
  ↑             ↑            ↑
furigana      furigana     furigana
```

 Essa estratégia oferece maior controle, mas aumenta a complexidade do gerador.

 ### Estratégia 3 — tags e posicionamento ASS

 Outra possibilidade é utilizar tags de posicionamento e estilos ASS para construir a composição diretamente dentro da legenda.

 A estratégia final deverá ser escolhida depois que o `FuriganaDocument` estiver funcionando.

 ## Estilos

 Os estilos ASS deverão ser mantidos separados da lógica de geração sempre que possível.

 Exemplo conceitual:

```
MainSubtitle
Furigana
```

 Isso permitirá alterar:

 - fonte;
- tamanho;
- cor;
- margem;
- estilo;

 sem modificar a lógica responsável pela composição do texto.

 ## Sincronização

 O timestamp da legenda original deve ser preservado.

 Se uma entrada SRT tiver:

```
00:01:12,500 --> 00:01:15,200
```

 a entrada ASS correspondente deverá utilizar o mesmo intervalo temporal.

 O processo de adicionar furigana não deve alterar a sincronização.

 ## Caracteres japoneses

 O gerador deverá preservar integralmente:

 - kanji;
- hiragana;
- katakana;
- pontuação japonesa;
- espaços relevantes;
- caracteres Unicode presentes na legenda.

 A codificação de saída deverá ser UTF-8.

 ## Questões ainda não resolvidas

 Antes da implementação definitiva do `AssGenerator`, será necessário testar:

 - posicionamento de furigana sobre palavras de diferentes comprimentos;
- combinação de kanji e okurigana;
- pontuação;
- quebra de linha;
- duas ou mais linhas de legenda;
- fontes japonesas;
- tamanho relativo do furigana;
- distância entre furigana e texto principal;
- colisão entre furigana e outras linhas;
- caracteres com largura variável;
- textos muito longos.

 ## Exemplo conceitual de transformação

 Entrada:

```
今日は日本語を勉強します。
```

 Modelo intermediário:

```
今日       → きょう
は         → -
日本語     → にほんご
を         → -
勉強       → べんきょう
します     → -
。         → -
```

 Saída visual esperada:

```
 きょう          にほんご       べんきょう
今日は        日本語を        勉強します。
```

 A representação acima é apenas ilustrativa. O `AssGenerator` poderá utilizar uma composição diferente caso isso produza melhor alinhamento.

 ## Relação com MPV

 O MPV será responsável apenas pela reprodução e exibição do arquivo ASS.

 O pipeline de geração será independente:

```
SRT
 ↓
Furigana Parser
 ↓
ASS
 ↓
MPV
```

 Posteriormente poderá ser criada uma integração mais dinâmica, na qual o MPV utilize scripts para solicitar ou atualizar as leituras durante a reprodução.

 Essa integração fica fora do escopo inicial.

