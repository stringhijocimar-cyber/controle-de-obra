# ObraControl

Aplicativo Android para cadastrar etapas, acompanhar a execução da obra, controlar compras e pagamentos e emitir o relatório executivo em PDF. O cadastro fica no aparelho. O aplicativo funciona sem internet.

## Controle da obra

- **Painel** reúne previsão parcial, pagamentos, saldo conhecido, acréscimos e avanço físico médio entre as etapas.
- **Agenda**, no Painel, mostra as etapas, entregas e pagamentos em aberto por prazo. Os atalhos abrem o cadastro correspondente ou iniciam um pagamento já vinculado.
- **Diário de obra**, no Painel, registra atividades, ocorrências e anexos por data, com vínculo opcional a uma etapa.
- **Etapas** registra datas previstas e reais, avanço de 0 a 100%, orçamento e data prevista de pagamento. Em **Etapas → Gantt**, deslize a linha do tempo, compare previsto e realizado e toque em uma etapa para editar.
- **Compras** registra fornecedor, documento, desconto, vínculo com uma etapa, classificação de acréscimo e itens com quantidades, unidade, preço unitário e subtotal. A previsão de entrega e o recebimento são registrados no cadastro da compra.
- **Pagamentos** aceita vários desembolsos por etapa ou compra, incluindo adiantamentos e pagamentos parciais. Cada registro inclui valor, data, pagador, meio, referência e comprovantes em PDF ou imagem.
- **Pendências** registra ações e aponta valores em aberto, divergências de documentos, pagamentos a maior e etapas sem data ou valor.
- **Relatório** gera o PDF completo com a agenda e o diário. Também exporta o cadastro e os anexos em ZIP e importa backups ZIP e JSON das versões anteriores.

O valor do documento tem prioridade sobre a soma dos itens. A diferença após descontos aparece na conciliação. O contrato global de mão de obra, quando preenchido, é a referência do balanço. Valores ainda a cotar permanecem em aberto. Pagamentos são somados pelos lançamentos, independentemente da etapa estar concluída.

Ao tocar em um campo de data, selecione um dia no calendário e toque em **Aplicar**. **Hoje** preenche a data atual e **Limpar data** remove uma data opcional.

## Leitura e anexos

Em **Compras → Nova compra**, escolha **Tirar foto** ou **Selecionar arquivo** em **Ler nota ou orçamento**. O app reconhece valores monetários em PDF, imagens JPG, PNG, WebP e HEIC e no XML de NF-e. Confira a sugestão e escolha se ela preenche o total ou o desconto. Descrição, fornecedor, quantidades e demais campos são preenchidos pelo usuário. O pagamento é lançado na aba Pagamentos.

Em **Pagamentos**, abra um lançamento e adicione os comprovantes com a câmera ou com o seletor de arquivos. Use **Ler valor pago** se quiser conferir um valor reconhecido no comprovante. Os arquivos ficam vinculados ao salvar e podem ser reabertos no cadastro. Cada documento pode ter até 25 MB. A leitura é feita no aparelho. PDFs mais longos podem ser lidos em blocos de páginas.

## Instalação e primeiro uso

O APK requer Android 8.0 ou mais recente. A edição 1.4.0 aparece como **ObraControl Gestão** e pode ser instalada ao lado da anterior. Para levar lançamentos e comprovantes do app anterior, abra **Relatório → Exportar backup com anexos** nele. No **ObraControl Gestão**, abra **Relatório → Importar dados da obra**, selecione o ZIP e confirme. Para começar do zero, cadastre a obra em **Dados da obra**. Quando o cadastro já tem a mesma obra, **Mesclar lançamentos** acrescenta registros e anexos ausentes e preserva as edições locais. **Substituir obra** exige confirmação. Use o backup ZIP para transferir também os documentos. Para exportar o relatório, use **Relatório → Gerar PDF completo**.

## Compilar

Use JDK 17 e Android SDK 35 com Build Tools 35.0.0. Abra a raiz no Android Studio ou execute:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Em Windows, use `gradlew.bat`. O instalador é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
