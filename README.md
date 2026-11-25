# ObraControl

Aplicativo Android para cadastrar etapas, acompanhar a execução da obra, controlar compras e pagamentos e emitir o relatório executivo em PDF. O cadastro fica no aparelho. O aplicativo funciona sem internet.

## Controle da obra

- **Painel** reúne previsão parcial, pagamentos, saldo conhecido, acréscimos e avanço físico médio entre as etapas.
- **Agenda**, no Painel, mostra as etapas, entregas e pagamentos em aberto por prazo. Os atalhos abrem o cadastro correspondente ou iniciam um pagamento já vinculado.
- **Diário de obra**, no Painel, registra atividades, ocorrências e anexos por data, com vínculo opcional a uma etapa.
- **Etapas** mostra uma tabela com valor previsto, pago, saldo, avanço físico e datas. O ícone e seu texto indicam a situação financeira. Em **Etapas → Gantt**, veja o custo de cada etapa e os pontos de pagamento na linha do tempo. Toque na etapa para editar.
- **Etapas → Desembolsos** mostra o total pago e o saldo de mão de obra a pagar por semana. O app abate os pagamentos da etapa e usa a data cadastrada em **Pagamento previsto**. Quando esse campo está vazio, o fim previsto da etapa aparece como estimativa. Lançamentos sem data aparecem em **Sem data**.
- **Compras** mostra uma tabela com total, pago, saldo, parcelamento, pagador, método, vencimento, entrega e documentos. O cadastro inclui **Condição de pagamento**, **Método de pagamento**, **Quem pagou** e **Cadastrar pagador**. Escolha **À vista**, **Parcelado** ou **Não informado**. No parcelamento, informe a quantidade e confira os valores calculados. O cálculo distribui os centavos entre as parcelas. Os nomes cadastrados e os pagadores dos lançamentos anteriores ficam disponíveis para seleção nas próximas compras e pagamentos.
- Ao ativar **Registrar pagamento nesta compra**, informe o valor pago, a data e os comprovantes. O pagamento é salvo junto da compra e atualiza o painel, o saldo e a prestação de contas. Para pagamentos parciais, informe o valor efetivamente pago.
- **Pagamentos** reúne as tabelas **Registrados** e **A pagar**. Os registros mostram valor, data, pagador, método, condição da compra e comprovantes. Em **A pagar**, toque no compromisso para registrar um pagamento já vinculado à etapa ou compra. O app aceita adiantamentos e pagamentos parciais.
- **Pendências** registra ações e aponta valores em aberto, divergências de documentos, pagamentos a maior e etapas sem data ou valor.
- **Relatório** gera o PDF completo com Gantt, desembolsos de mão de obra por semana, agenda e diário. Também exporta o cadastro e os anexos em ZIP e importa backups ZIP e JSON das versões anteriores.

O valor do documento tem prioridade sobre a soma dos itens. A diferença após descontos aparece na conciliação. O contrato global de mão de obra, quando preenchido, é a referência do balanço. Valores ainda a cotar permanecem em aberto. Pagamentos são somados pelos lançamentos, independentemente da etapa estar concluída.

As tabelas usam uma linha compacta por registro, cabeçalho claro e fixo, divisórias suaves e colunas separadas para cada informação. O nome fica fixo durante a rolagem horizontal. Os valores estão em reais, alinhados à direita, e a quantidade de parcelas tem sua própria coluna. Toque em uma linha para abrir o cadastro completo.

Toque no custo e escolha **Editar dados** para abrir o cadastro correspondente. Funciona em Compras, Etapas, Pagamentos e em **Tela cheia**. Em **Pagamentos → A pagar**, essa opção edita a compra ou etapa de origem; tocar no nome continua iniciando um pagamento vinculado. Nos pagamentos registrados, a edição abre o lançamento pago. Ao salvar, os totais e saldos são recalculados.

Os totais ficam logo abaixo das linhas da tabela e permanecem visíveis durante a rolagem. **Compras** e **Etapas** mostram previsto, pago e saldo. **Pagamentos → Registrados** totaliza os pagamentos exibidos; **A pagar** totaliza o saldo e os valores já pagos nos compromissos dessa tabela. A soma considera os registros de cada tabela, incluindo as linhas fora da área visível. Etapas totaliza o orçamento distribuído entre as etapas; o contrato global continua no Painel. Custos não informados aparecem como **A definir**, com indicação de **Total parcial**. Tabelas vazias mostram totais de zero.

O ícone na coluna **Sit.** fica verde para pagamentos quitados, amarelo para pagamento parcial ou conciliação a confirmar, vermelho para valores em aberto ou atrasados e cinza para valores a definir ou sem custo. Os ícones têm descrições para leitores de tela. A legenda fica abaixo da tabela.

Use **Tela cheia** para ampliar a tabela. Com o celular na horizontal, mais colunas ficam visíveis. **Fechar tabela** volta à aba original; tocar em um registro abre sua compra, etapa ou pagamento.

Compras de versões anteriores aparecem com parcelamento **Não informado** até o preenchimento desse campo. A quantidade de parcelas é uma condição da compra. O valor pago é calculado pelos pagamentos efetivamente registrados.

Ao tocar em um campo de data, selecione um dia no calendário e toque em **Aplicar**. **Hoje** preenche a data atual e **Limpar data** remove uma data opcional.

## Leitura e anexos

Em **Compras → Nova compra**, escolha **Tirar foto** ou **Selecionar arquivo** em **Ler nota ou orçamento**. O app reconhece valores monetários em PDF, imagens JPG, PNG, WebP e HEIC e no XML de NF-e. Confira a sugestão e escolha se ela preenche o total ou o desconto. Descrição, fornecedor, quantidades e demais campos são preenchidos pelo usuário. O pagamento é lançado na aba Pagamentos.

Em **Pagamentos**, abra um lançamento e adicione os comprovantes com a câmera ou com o seletor de arquivos. Use **Ler valor pago** se quiser conferir um valor reconhecido no comprovante. Os arquivos ficam vinculados ao salvar e podem ser reabertos no cadastro. Cada documento pode ter até 25 MB. A leitura é feita no aparelho. PDFs mais longos podem ser lidos em blocos de páginas.

## Instalação e primeiro uso

A versão 1.8.3 requer Android 8.0 ou mais recente e atualiza diretamente o **ObraControl 1.7.0 / 1.8.0 / 1.8.1 / 1.8.2**, usando a mesma assinatura permanente e o cadastro salvo no aparelho. Abra o instalador e escolha **Atualizar**.

Para transferir dados das edições **ObraControl Financeiro** ou **ObraControl Gestão**, abra **Relatório → Exportar backup com anexos** no app anterior. No **ObraControl**, abra **Relatório → Importar dados da obra**, selecione o ZIP e confirme. Confira o cadastro importado antes de desinstalar a edição anterior. Os backups JSON e ZIP das versões anteriores do formato continuam aceitos.

Para começar do zero, cadastre a obra em **Dados da obra**. Quando o cadastro já tem a mesma obra, **Mesclar lançamentos** acrescenta registros, pagadores e anexos ausentes e preserva as edições locais. **Substituir obra** exige confirmação. Para exportar o relatório, use **Relatório → Gerar PDF completo**.

## Compilar

Use JDK 17 e Android SDK 35 com Build Tools 35.0.0. Abra a raiz no Android Studio ou execute:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Em Windows, use `gradlew.bat`. O instalador é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

O workflow do GitHub gera o APK de testes. Para compilar a distribuição assinada, forneça `OBRACONTROL_KEYSTORE_FILE` e `OBRACONTROL_KEYSTORE_PASSWORD`, usando o alias `obracontrol`, e execute `./gradlew :app:assembleRelease`. A chave e sua senha ficam no arquivo privado de assinatura do projeto. As próximas distribuições devem reutilizar essa chave e o identificador `br.com.obracontrol.app` para atualizar o aplicativo instalado.
