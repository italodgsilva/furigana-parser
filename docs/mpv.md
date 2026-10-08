# Integração com o MPV

## Objetivo

Reproduzir um vídeo com a legenda de furigana gerada pelo pipeline, sem
intermediários manuais.

O fluxo é:

```text
SRT
 ↓
Furigana Parser (pipeline)
 ↓
ASS (ao lado da SRT)
 ↓
MPV (--sub-file)
```

## Como funciona

A camada `mpv` é deliberadamente simples e desacoplada do pipeline. Ela apenas
monta e executa o comando:

```text
mpv --sub-file=<legenda.furigana.ass> <video>
```

Ela **não** conhece SRT, Kuromoji, Jisho nem o `FuriganaDocument`. A geração do
ASS continua a cargo da camada de saída (`AssGenerator`), orquestrada pelo
`Application`.

A execução do processo é abstraída por `ProcessRunner`, o que permite testar a
integração sem abrir o player. A implementação padrão (`DefaultProcessRunner`)
usa `ProcessBuilder` com `inheritIO()`, para que o terminal do MPV funcione
normalmente.

## Uso

```text
furigana-parser --mpv <entrada.srt> <video>
```

Alias: `play`.

Passos executados:

1. o pipeline processa a SRT;
2. o ASS é gravado ao lado da SRT, com o sufixo `.furigana.ass`;
3. o vídeo é aberto no MPV com `--sub-file` apontando para o ASS gerado.

Exemplos de nome do arquivo gerado:

```text
episodio.srt     → episodio.furigana.ass
episodio.ja.srt  → episodio.ja.furigana.ass
```

## Limitações

- **MPV obrigatório no `PATH`.** Se o executável não for encontrado, a mensagem
  de erro orienta a instalação e o código de saída é de erro. A ferramenta não
  tenta instalar nem localizar o MPV em caminhos alternativos.
- **Legenda sobrescrita.** O ASS é regenerado a cada execução e grava sempre no
  mesmo caminho derivado da SRT, sobrescrevendo o arquivo existente.
- **Sem atualização dinâmica.** A integração apenas inicia o player. Não há
  comunicação em tempo real nem regeneração da legenda durante a reprodução.
- **Resolução fixa.** O ASS usa `PlayResX/PlayResY = 1920x1080` e posicionamento
  por grade de caracteres. Vídeos com proporção diferente de 16:9 podem deslocar
  o furigana; trechos em latim têm alinhamento aproximado (ver
  `docs/ass-format.md`).
- **Fonte CJK.** O alinhamento pressupõe uma fonte com caracteres de largura
  total (kanji/kana ocupando ~1 em). Se a fonte configurada não existir no
  sistema, o MPV faz fallback e as métricas podem variar.
- **Faixas de legenda.** `--sub-file` adiciona uma legenda externa, mas faixas
  embutidas no vídeo não são gerenciadas; pode ser necessário selecionar a faixa
  no MPV.
- **Vídeo informado explicitamente.** Não há descoberta automática do vídeo a
  partir da SRT (nomes/ pastas correlatos não são adivinhados).
- **Sem ajuste de sincronização.** O `Application` não aplica offset de tempo; a
  legenda herdada da SRT é usada como está.
- **Leituras dependem de rede no primeiro uso.** O fallback do Jisho exige
  internet para termos desconhecidos pelo Kuromoji; depois disso o cache local
  permite uso offline.
- **Execução em primeiro plano.** O processo Java aguarda o MPV terminar; não há
  modo destacado/daemon.
- **Somente Linux.** O projeto é desenvolvido e testado em Linux; o
  comportamento no Windows não é suportado.

## Escopo futuro

A arquitetura prevê, para depois:

- script(s) Lua no MPV;
- atalhos;
- carregamento automático de legendas;
- comunicação da aplicação com o MPV;
- atualização dinâmica da legenda durante a reprodução.

Nada disso faz parte desta etapa.
