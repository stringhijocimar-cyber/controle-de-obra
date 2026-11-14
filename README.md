# ObraControl

Aplicativo Android para cadastrar etapas, acompanhar a execução da obra, controlar compras e pagamentos e emitir o relatório executivo em PDF. O cadastro fica no aparelho. O aplicativo funciona sem internet.

## Controle da obra

- **Painel** reúne previsão parcial, pagamentos, saldo conhecido, acréscimos e avanço físico médio entre as etapas.
- **Etapas** registra datas previstas e reais, avanço de 0 a 100%, orçamento e data prevista de pagamento.
- **Compras** registra fornecedor, documento, desconto, vínculo com uma etapa, classificação de acréscimo e itens com quantidades, unidade, preço unitário e subtotal.
- **Pagamentos** aceita vários desembolsos por etapa ou compra, incluindo adiantamentos e pagamentos parciais. Cada registro inclui valor, data, pagador, meio e referência de comprovante.
- **Pendências** registra ações e aponta valores em aberto, divergências de documentos, pagamentos a maior e etapas sem data ou valor.
- **Relatório** gera o PDF integral e exporta ou restaura o cadastro em JSON, incluindo os backups da versão anterior.

O valor do documento tem prioridade sobre a soma dos itens. A diferença após descontos aparece na conciliação. O contrato global de mão de obra, quando preenchido, é a referência do balanço. Valores ainda a cotar permanecem em aberto. Pagamentos são somados pelos lançamentos, independentemente da etapa estar concluída.

## Instalação e primeiro uso

O APK requer Android 8.0 ou mais recente. Após abrir o app, cadastre a obra em **Dados da obra**. Para carregar um cadastro existente, abra **Relatório → Restaurar backup JSON**, selecione o arquivo e confirme. Para exportar o relatório, use **Relatório → Gerar PDF completo**.

## Compilar

Use JDK 17 e Android SDK 35 com Build Tools 35.0.0. Abra a raiz no Android Studio ou execute:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Em Windows, use `gradlew.bat`. O instalador é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
