# Embutição de fonte no ASS

## Objetivo

Fazer o próprio arquivo ASS carregar a fonte necessária para renderizar o
furigana, sem exigir que a fonte esteja instalada na máquina de quem assiste.

O problema: o ASS referencia a fonte por nome (`Noto Sans CJK JP`). Se essa fonte
não estiver instalada, o player usa outra — ou nenhuma — e os kanji e as leituras
podem aparecer com largura e posicionamento errados.

A solução: gravar os bytes da fonte dentro do ASS, na seção `[Fonts]`. O player
registra essa fonte no provedor do sistema e a usa ao desenhar a legenda.

Tudo é feito em Java, dentro do pipeline, sem depender de ferramentas externas.

## Como funciona

O processo tem três passos:

```text
Estilo do ASS (Fontname)
   ↓
Localizar o arquivo da fonte no sistema
   ↓
Codificar os bytes e escrever a seção [Fonts]
```

1. **Localizar a fonte** — `FontLocator` varre os diretórios usuais do sistema
   (Linux, macOS e Windows) e lê a tabela `name` de cada arquivo, comparando o
   nome de família. O casamento ignora maiúsculas e espaços repetidos. Se a fonte
   estiver dentro de uma coleção (`.ttc`), apenas a face correspondente é
   recortada em uma fonte autônoma (`SfntReader`), pois o ASS embute uma face por
   vez.
2. **Codificar** — `AssFontEmbedder` converte os bytes no mesmo formato uuencode
   do libass: cada 3 bytes viram 4 caracteres de 6 bits, somados a 33, em linhas
   de tamanho fixo. As quebras de linha são ignoradas na leitura.
3. **Inserir** — a seção é gravada entre `[V4+ Styles]` e `[Events]`:

   ```text
   [Fonts]
   fontname: NotoSansJP.otf
   4V254Q!1!1!!"!!!1E&42?X[^29...
   ...
   [Events]
   ```

   `fontname:` é o nome do arquivo sugerido para a fonte; as linhas seguintes são
   os bytes codificados.

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

A opção é válida para a geração de ASS e para o modo `--mpv`; na geração de JSON
não tem efeito (o JSON não carrega fonte).

Se a família não for encontrada no sistema, a geração falha com uma mensagem
pedindo o caminho via `--embed-font=<arquivo>`.

## Tamanho do arquivo

Como o arquivo inteiro é embutido — sem subsetting —, o ASS fica grande. A fonte
padrão `Noto Sans CJK JP` tem cerca de 16,5 MB, o que produz um ASS de
aproximadamente 22 MB. O processamento é rápido (a codificação leva alguns
milissegundos), mas o arquivo é pesado para transportar.

Esse é o custo de não depender de uma ferramenta externa de subsetting. Para
reduzir o tamanho, é possível passar com `--embed-font=<arquivo>` uma fonte
menor, desde que ela cubra os caracteres usados na legenda.

## Verificação

Para confirmar que a legenda usa a fonte embutida, renderize o ASS em um ambiente
sem fontes instaladas (por exemplo, com um `fontconfig` vazio):

```
FONTCONFIG_FILE=/caminho/fontconfig-vazio ffmpeg -itsoffset <t> \
  -f lavfi -i color=c=black:s=1920x1080:d=0.1 \
  -vf "ass=saida.ass,format=gray" -frames:v 1 -f rawvideo -pix_fmt gray out.gray
```

Sem a seção `[Fonts]`, nenhuma legenda é desenhada; com ela, o texto aparece
mesmo sem a fonte instalada no sistema.

## Escopo

O `AssGenerator` continua sem conhecer fontes: a embedação é um pós-processamento
do texto ASS, acionado pela CLI (`Application`). Assim, a geração do ASS segue
independente dos detalhes de onde a fonte está instalada.

## Melhorias futuras

Reduzir o tamanho do ASS embutido por meio de subsetting da fonte (gravar apenas
os glifos usados na legenda). Hoje o arquivo inteiro é embutido para não depender
de ferramentas externas; o subsetting de fontes CFF/CID, como a `Noto Sans CJK
JP`, exigiria uma biblioteca especializada.
