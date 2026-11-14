# ObraControl

Aplicativo Android para cadastrar etapas, acompanhar a execução da obra, controlar compras e pagamentos e emitir o relatório executivo em PDF. O cadastro fica no aparelho. O aplicativo funciona sem internet.

## Controle da obra

- **Painel** reúne previsão parcial, pagamentos, saldo conhecido, acréscimos e avanço físico médio entre as etapas.
- **Etapas** registra datas previstas e reais, avanço de 0 a 100%, orçamento e data prevista de pagamento.
- **Compras** registra fornecedor, documento, desconto, vínculo com uma etapa, classificação de acréscimo e itens com quantidades, unidade, preço unitário e subtotal.
- **Pagamentos** aceita vários desembolsos por etapa ou compra, incluindo adiantamentos e pagamentos parciais. Cada registro inclui valor, data, pagador, meio, referência e comprovantes em PDF ou imagem.
- **Pendências** registra ações e aponta valores em aberto, divergências de documentos, pagamentos a maior e etapas sem data ou valor.
- **Relatório** gera o PDF integral e exporta o cadastro e os anexos em um ZIP e importa backups ZIP e os JSON das versões anteriores.

O valor do documento tem prioridade sobre a soma dos itens. A diferença após descontos aparece na conciliação. O contrato global de mão de obra, quando preenchido, é a referência do balanço. Valores ainda a cotar permanecem em aberto. Pagamentos são somados pelos lançamentos, independentemente da etapa estar concluída.

## Leitura e anexos

Em **Compras → Nova compra**, escolha **Tirar foto** ou **Selecionar arquivo** em **Ler nota ou orçamento**. O app reconhece valores monetários em PDF, imagens JPG, PNG, WebP e HEIC e no XML de NF-e. Confira a sugestão e escolha se ela preenche o total ou o desconto. Descrição, fornecedor, quantidades e demais campos são preenchidos pelo usuário. O pagamento é lançado na aba Pagamentos.

Em **Pagamentos**, abra um lançamento e adicione os comprovantes com a câmera ou com o seletor de arquivos. Use **Ler valor pago** se quiser conferir um valor reconhecido no comprovante. Os arquivos ficam vinculados ao salvar e podem ser reabertos no cadastro. Cada documento pode ter até 25 MB. A leitura é feita no aparelho. PDFs mais longos podem ser lidos em blocos de páginas.

## Instalação e primeiro uso

O APK requer Android 8.0 ou mais recente. Após abrir o app, cadastre a obra em **Dados da obra**. Para carregar um cadastro existente, abra **Relatório → Importar dados da obra** e selecione o arquivo. Quando o cadastro já tem a mesma obra, **Mesclar lançamentos** acrescenta registros e anexos ausentes e preserva as edições locais. **Substituir obra** exige confirmação. Use o backup ZIP para transferir também os documentos. Para exportar o relatório, use **Relatório → Gerar PDF completo**.

## Compilar

Use JDK 17 e Android SDK 35 com Build Tools 35.0.0. Abra a raiz no Android Studio ou execute:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Em Windows, use `gradlew.bat`. O instalador é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
