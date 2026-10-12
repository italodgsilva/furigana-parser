# Embutição de fonte no ASS

## Objetivo

Fazer o próprio arquivo ASS carregar a fonte necessária para renderizar o
furigana, sem exigir que a fonte esteja instalada na máquina de quem assiste.

O problema: o ASS referencia a fonte por nome (`Noto Sans CJK JP`). Se essa fonte
não estiver instalada, o player usa outra — ou nenhuma — e os kanji e as leituras
podem aparecer com largura e posicionamento errados.

A solução: gravar os bytes da fonte dentro do ASS, na seção `[Fonts]`. O player
registra essa fonte no provedor do sistema e a usa ao desenhar a legenda.

A embedação é feita em Java, dentro do pipeline. A redução opcional da fonte
(`--subset-font`) usa a biblioteca do HarfBuzz, acessada por JNA — sem processo
externo (veja [Subsetting](#subsetting-da-fonte)).

## Como funciona

```text
Estilo do ASS (Fontname)
   ↓
Localizar o arquivo da fonte no sistema
   ↓
[com --subset-font] Reduzir aos glifos usados (HarfBuzz)
   ↓
Codificar os bytes e escrever a seção [Fonts]
```

1. **Localizar a fonte** — `FontLocator` varre os diretórios usuais do sistema
   (Linux, macOS e Windows) e lê a tabela `name` de cada arquivo, comparando o
   nome de família. O casamento ignora maiúsculas e espaços repetidos. Se a fonte
   estiver dentro de uma coleção (`.ttc`), apenas a face correspondente é
   recortada em uma fonte autônoma (`SfntReader`), pois o ASS embute uma face por
   vez.
2. **Reduzir (opcional)** — com `--subset-font`, `HarfBuzzSubsetter` mantém
   apenas os glifos usados pela legenda. Veja a seção seguinte.
3. **Codificar** — `AssFontEmbedder` converte os bytes no mesmo formato uuencode
   do libass: cada 3 bytes viram 4 caracteres de 6 bits, somados a 33, em linhas
   de tamanho fixo. As quebras de linha são ignoradas na leitura.
4. **Inserir** — a seção é gravada entre `[V4+ Styles]` e `[Events]`:

   ```text
   [Fonts]
   fontname: NotoSansJP.otf
   4V254Q!1!1!!"!!!1E&42?X[^29...
   ...
   [Events]
   ```

   `fontname:` é o nome do arquivo sugerido para a fonte; as linhas seguintes são
   os bytes codificados.

## Subsetting da fonte

A `Noto Sans CJK JP` tem cerca de 65 mil glifos (todos os kanji CJK), mas uma
legenda usa apenas algumas centenas. Sem redução, o ASS embutido fica com dezenas
de MB. Com `--subset-font`, só os glifos usados são gravados.

O processo:

1. **Coletar os code points** — `UsedCodePoints` percorre o `FuriganaDocument` e
   reúne os caracteres do texto principal e das leituras (o furigana). Os
   override tags (`\pos` etc.) não são glifos e não entram.
2. **Reduzir** — `HarfBuzzSubsetter` usa a `libharfbuzz-subset` por JNA. As
   funções `hb_subset_*` ficam nessa biblioteca; as demais (`hb_blob_*`,
   `hb_face_*`, `hb_set_*`) vêm da `libharfbuzz`, que é sua dependência — por isso
   resolvem a partir do mesmo identificador carregado. O hinting é descartado
   (`HB_SUBSET_FLAGS_NO_HINTING`) e a tabela `name` é preservada, o que mantém a
   família (`Noto Sans CJK JP`) e permite ao player casar a fonte com o
   `Fontname` do estilo.
3. **Embutir** — os bytes reduzidos seguem o fluxo normal de codificação e
   inserção.

O `AssFontEmbedder` não conhece o subsetting: recebe a `FontFile` já reduzida. A
operação é abstraída por `FontSubsetter`, e a CLI cuida de acioná-la.

### Onde a biblioteca é procurada

Como o nome do arquivo varia entre sistemas (no Linux a instalação de runtime
traz só o soname versionado, ex.: `libharfbuzz-subset.so.0`), a busca tenta
primeiro a resolução padrão do JNA e depois varre os diretórios usuais
(`/usr/lib/...`, `/usr/local/lib`, Homebrew, etc.). Para apontar um arquivo
explicitamente, use a variável de ambiente `FURIGANA_HARFBUZZ_SUBSET` ou a
propriedade `-Dfurigana.harfbuzz.subset=<arquivo>`.

Se a biblioteca não for encontrada — ou se a redução de uma fonte falhar — o
programa avisa em `stderr` e embute a fonte completa. O ASS nunca é gerado sem os
glifos necessários. Quando a biblioteca está ausente, o aviso já traz o comando de
instalação para o sistema em uso (por exemplo, `sudo apt install libharfbuzz-subset0`
no Debian/Ubuntu ou `brew install harfbuzz` no macOS) e a lembrança da variável
`FURIGANA_HARFBUZZ_SUBSET`, usada para apontar um arquivo explicitamente.

## Uso

Sem argumento, a fonte é localizada pelo nome de família declarado no estilo
(`Noto Sans CJK JP`):

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font entrada.srt saida.ass
```

Com um arquivo explícito, o arquivo informado é embutido (útil quando a fonte não
está nos diretórios usuais ou quando se quer usar outra fonte):

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font=fonte.otf entrada.srt saida.ass
```

Para reduzir a fonte embutida aos glifos usados (exige `--embed-font`):

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font --subset-font entrada.srt saida.ass
```

As opções valem para a geração de ASS e para o modo `--mpv`; na geração de JSON
não têm efeito (o JSON não carrega fonte).

Se a família não for encontrada no sistema, a geração falha com uma mensagem
pedindo o caminho via `--embed-font=<arquivo>`.

## Tamanho do arquivo

Sem subsetting, o arquivo inteiro é embutido. A `Noto Sans CJK JP` tem cerca de
16,5 MB, o que produz um ASS de aproximadamente 22 MB.

Com `--subset-font`, apenas os glifos usados entram. Como referência (fonte
`NotoSansJP.otf`, mesma família do estilo):

| Legenda | Sem subsetting | Com `--subset-font` |
| --- | --- | --- |
| `japanese.srt` (3 entradas, ~40 caracteres) | ~22 MB | ~27 KB |
| Um episódio (~370 entradas) | ~22 MB | ~0,3 MB |

O tempo de geração não muda de forma relevante (a redução leva alguns
milissegundos; o custo está em tokenizar a legenda).

## Verificação

Para confirmar que a legenda usa a fonte embutida, renderize o ASS em um ambiente
sem fontes instaladas (por exemplo, com um `fontconfig` vazio):

```
FONTCONFIG_FILE=/caminho/fontconfig-vazio ffmpeg -itsoffset <t> \
  -f lavfi -i color=c=black:s=1920x1080:d=0.1 \
  -vf "ass=saida.ass,format=gray" -frames:v 1 -f rawvideo -pix_fmt gray out.gray
```

Sem a seção `[Fonts]`, nenhuma legenda é desenhada; com ela, o texto aparece
mesmo sem a fonte instalada no sistema. A saída com e sem subsetting é idêntica,
byte a byte, no mesmo instante.

## Escopo

O `AssGenerator` continua sem conhecer fontes: a embedação é um
pós-processamento do texto ASS, acionado pela CLI (`Application`). Localizar,
reduzir e codificar são etapas separadas (`FontLocator`, `FontSubsetter` /
`HarfBuzzSubsetter`, `AssFontEmbedder`), e a geração do ASS segue independente
desses detalhes.

## Testes

O fluxo de chamadas ao HarfBuzz é coberto com uma implementação falsa da interface
JNA (`HarfBuzzSubsetterTest`), sem exigir a biblioteca nativa. O teste que reduz
uma fonte real só roda quando `libharfbuzz-subset` e uma fonte de exemplo estão
disponíveis.

## Melhorias futuras

- **Subsetting sem biblioteca nativa** — uma porta Java pura (por exemplo, um
  subsetter `glyf` para fontes TrueType) eliminaria a dependência de sistema,
  mas não cobre CFF/CID como a `Noto Sans CJK JP`.
- **Distribuir a biblioteca** — empacotar a `libharfbuzz-subset` por plataforma
  faria o subsetting funcionar sem instalação prévia.
