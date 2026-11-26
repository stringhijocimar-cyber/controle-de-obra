# ObraControl

Aplicativo Android para planejar etapas e custos, acompanhar a execução da obra, controlar compras e pagamentos e emitir o relatório executivo em PDF. O cadastro e os modelos funcionam sem internet. O fluxo opcional com ChatGPT requer acesso ao serviço.

## Versão 2.3.1 — conferência de pagamentos repetidos

- **Pagamentos → Registrados → Conferir duplicados** agrupa os lançamentos por custo, mostra possíveis repetições e valores pagos a maior. Use **Todos os custos** para conferir registros com datas ou dados diferentes. Abra **Revisar pagamentos**, mantenha os reais e confira o total antes de salvar. Os registros retirados e seus comprovantes permanecem no histórico e no backup.
- O cadastro de um novo pagamento avisa quando já existe outro com os mesmos dados. É possível abrir o lançamento existente para editar. **Confirmar outro pagamento igual** permite um desembolso real distinto mediante escolha explícita. Editar e salvar o mesmo ID continua atualizando seu registro.
- Parcelas vinculadas e referências bancárias distintas são tratadas separadamente. A lista mostra a parcela ou a data no detalhe da linha, para distinguir pagamentos de mesmo valor. A atualização preserva todos os lançamentos existentes; a revisão aplica somente as escolhas confirmadas pelo usuário.
- Mantém o formato de backup 11, o relatório completo, a programação de parcelas e a assinatura permanente.

## Versão 2.3.0 — parcelas com datas e flags de pagamento

- No cadastro da compra parcelada, a lista mostra cada parcela, valor, vencimento e flag de pago. Informe o **Primeiro vencimento** para calcular as datas mensais. Se marcar a primeira como paga antes de definir o vencimento, a data real desse pagamento inicia o calendário das demais. Os centavos são distribuídos para fechar o total, inclusive nos meses mais curtos.
- Marque somente parcelas realmente pagas e confira a **Data do pagamento da parcela** no calendário. As demais ficam em A pagar. Salvar novamente atualiza o mesmo pagamento; marcar uma parcela parcialmente coberta registra somente o saldo restante.
- O **+** nas tabelas de **Compras** e **Pagamentos → Parcelados** abre as parcelas abaixo da compra. Toque no flag para pagar ou editar a data. Uma parcela paga por um único lançamento pode ser marcada como não paga; seu lançamento e comprovantes são guardados no histórico de revisão.
- Pagamentos anteriores são mantidos e abatidos uma única vez. Se um pagamento antigo já cobriu o total da compra, as parcelas aparecem quitadas; use **Corrigir / editar pago efetivo** para revisar esse registro antes de cadastrar novos pagamentos. Criar a programação nunca lança o total outra vez.
- A programação alimenta a tabela semanal e o PDF completo, que ganha **Controle de parcelas** com vencimentos, datas reais e situação. Mantém o formato de backup 11, os anexos e a assinatura permanente das versões anteriores.

## Versão 2.2.0 — correção do pago e desembolsos em tabela

- Reabrir uma compra e editar um pagamento reutiliza o lançamento existente. Ao habilitar o pagamento, **Editar pagamento existente** é a opção inicial quando já há registros; **Novo pagamento adicional** é uma escolha explícita para somar outro desembolso real. Valores, datas, comprovantes e vínculos da parcela são preservados na edição.
- Toque em **Pago efetivo → Editar dados**, ou abra o custo e **Corrigir / editar pago efetivo**, para revisar o total. Marque os pagamentos reais, desmarque os duplicados e confira o total após a revisão antes de salvar. Os registros retirados ou valores anteriores ficam no histórico, com motivo e comprovantes; registros retirados podem ser restaurados. Importar um backup antigo por mesclagem não reativa IDs já retirados pela revisão local.
- **Material** e **Serviço** são as duas opções principais em Compras e Pagamentos. **Mão de obra é um serviço**, exibido dentro da etapa. As duas telas usam o mesmo cadastro de **Etapa vinculada**; no pagamento, **Custo vinculado** mostra os materiais ou serviços dessa etapa. Cadastre uma contratação uma única vez e selecione o custo existente ao pagar. O aplicativo mantém os registros antigos e não combina automaticamente custos de mão de obra e compras de serviços.
- **Etapas → Desembolsos** e **Pagamentos → Semanas** usam a mesma tabela: **tipo → etapa → custo → pagamento / parcela**. O **+** abre a camada. Há colunas de responsável, método, pago efetivo, saldo e valores semanais, com ano e mês no cabeçalho. Os grupos somam os registros filhos; o rodapé totaliza a obra independentemente de quais camadas estão abertas. **Total**, **A pagar** e **Pago efetivo** mudam os valores das colunas semanais. Semanas vazias permanecem no cronograma; períodos extensos são paginados em blocos de até 26 semanas.
- **Ver semanas** e **Responsáveis e saldos** levam às colunas correspondentes na rolagem horizontal; a coluna de etapa / descrição permanece fixa. Pagos seguem a data real, saldos seguem os vencimentos, e valores ou responsáveis ainda não informados ficam identificados.
- Indicadores financeiros com traços e fundos suaves substituem as caixas quadradas. Pago, parcial, em aberto, atrasado e pago a maior mantêm descrição acessível e ação de pagamento / edição.
- O relatório gerencial integral acrescenta a tabela semanal em camadas e o histórico da revisão do pago efetivo. Histórico e comprovantes revisados entram no backup **formato 11**, com leitura dos formatos **1 a 10**. A atualização não exclui duplicados automaticamente; a revisão dos lançamentos existentes depende da escolha do usuário no aplicativo.

## Versão 2.1.0 — cronograma, cartão e relatório gerencial

- Em **Etapas → Gantt**, o seletor **Dias / Semanas / Meses** ajusta a escala temporal. O cabeçalho agrupa **ano → mês → semana ISO → dia**, conforme a escala. Semanas começam na segunda-feira e usam o número e o ano ISO, inclusive na virada do ano. Nomes das etapas ficam fixos durante a rolagem; barras e pontos de pagamento usam as mesmas datas em todas as escalas.
- A escala escolhida fica salva com a obra e também pode ser escolhida em **Relatório**. O PDF mantém os agrupamentos de calendário; em períodos extensos, espaça rótulos para preservar a leitura. O app permite ampliar a escala e rolar pelo período completo.
- Nas tabelas **Compras** e **Pagamentos**, toque em **Parcelas** ou **Condição / valores** para editar a compra vinculada. Edite método da compra, condição, quantidade e total; confira a divisão exata dos centavos, o pago efetivo e o saldo. **Método** em Compras edita a condição da compra; **Método** em pagamentos registrados abre o lançamento para corrigir a forma efetivamente paga. Na mão de obra, Parcelas abre sua programação de vencimentos. A quantidade de parcelas da compra é uma condição comercial; as datas continuam em **Programar pagamentos**.
- Ao editar um pagamento de compra, marque **Editar cartão e parcelamento da compra** para ajustar essas condições junto com o pagamento. Os dois registros são salvos na mesma operação. Outros pagamentos, comprovantes, datas e parcelas programadas são preservados. Alterar o parcelamento não registra pagamentos futuros.
- O **Relatório gerencial completo** acrescenta uma posição executiva por material, serviço e mão de obra; previsão, pago e saldo; vencidos, próximos 7 dias, do 8º ao 30º dia, posteriores e sem data; cartão e parcelamentos; e próximas decisões. As seções completas anteriores continuam presentes: gráficos por pagador e método, Gantt, balanço, projeções, calendário semanal, planejamento e aprovações, materiais, pagamentos, pendências, agenda e diário.
- Dados no formato **10**, com leitura dos formatos **1 a 9**. A escala visual não altera custos ou pagamentos e uma obra vazia continua vazia para a importação mesmo após alterar a escala. Mesclar preserva a preferência visual local; substituir restaura a preferência do backup.

## Versão 2.0.0 — planejamento e orçamento

- No **Painel → Abrir planejamento**, crie um modelo de **Reforma**, **Revestimento**, **Casa nova** ou **Personalizado / IA**. Informe escopo, cidade/UF e área de referência. Os modelos criam uma estrutura editável de **planejamento → etapas → serviços → insumos**; cada insumo é material, mão de obra ou serviço contratado / frete / equipamento.
- Os modelos não inventam preços. No revestimento, a quantidade de revestimento usa a área e a perda informada, com a fórmula explícita. Argamassa, rejunte, bases e estrutura dependem de medição ou especificação e permanecem a definir. Ajuste ou exclua serviços e etapas opcionais antes da aprovação.
- Abra um serviço para editar seus insumos: quantidade, unidade, preço unitário, fonte / fornecedor, data da referência e indicação de estimativa. O subtotal é calculado por quantidade × preço unitário, com arredondamento em centavos. Preço ausente permanece a cotar, sem virar zero.
- **Revisar e aprovar orçamento** exige insumos completos e registra responsável, data, revisão e uma cópia dos insumos e preços aprovados. Alterar o rascunho abre uma nova revisão e preserva o histórico de aprovações. A estimativa atual e o último orçamento aprovado aparecem separadamente.
- **Ativar no controle da obra** permite criar uma etapa e seus custos previstos ou vincular uma etapa existente. A criação lança mão de obra somente no orçamento da etapa e materiais / serviços somente em Compras, sem dupla contagem. A vinculação não recria nem altera compras, pagamentos ou o contrato geral. Quando há contrato global de mão de obra, o usuário escolhe se o novo escopo deve ser somado a ele.
- O controle compara **último orçamento aprovado, previsão atual, pago efetivo, desvio da previsão e avanço físico**. A previsão atual considera a mão de obra e todas as compras / serviços da etapa vinculada; valores não definidos são identificados como parciais. As datas, vencimentos, parcelas, comprovantes e o calendário semanal continuam nos cadastros de execução existentes. Revisar o planejamento não reescreve custos ou pagamentos já cadastrados.
- O PDF integral acrescenta resumo do planejamento, composição dos serviços e histórico de aprovações. Backup no formato **9**, com leitura dos formatos **1 a 8**, mantém planejamentos, fontes, aprovações e vínculos. A mesclagem preserva edições locais e remapeia os vínculos quando etapas / compras correspondentes têm IDs diferentes.

### Planejar com ChatGPT

Abra um planejamento e toque em **Planejar com ChatGPT**. Copie o roteiro, abra o ChatGPT e envie-o. O roteiro inclui escopo, local, área e formato de resposta. Depois cole a resposta ou carregue o arquivo em **Importar resposta da IA → Conferir proposta → Salvar**. O app mostra a prévia das etapas antes de importar.

A IA é executada no ChatGPT, fora do aplicativo; não há chamada automática a um modelo nem chave de API embutida. A resposta entra como novo rascunho sem aprovação, vínculo de execução ou pagamento. A mesma proposta importada novamente preserva o rascunho local sem duplicação. Nenhum cadastro existente é substituído por essa importação.

Para precificar, use cotações locais ou uma referência verificável com data e local. A [consulta oficial do SINAPI](https://www.caixa.gov.br/poder-publico/modernizacao-gestao/sinapi/Paginas/default.aspx) distingue referências com e sem preço; o app não presume que uma referência sem preço tenha custo zero e não consulta automaticamente o SINAPI. Dimensionamento estrutural e quantitativos dependentes de projeto não são gerados por regra de área.

## Versão 1.9.0

- A coluna **Pago** nas tabelas é uma caixa de quitação. Ao marcar um custo em aberto, confira valor, data, pagador, método e comprovante antes de salvar o pagamento. Materiais, serviços de compras e mão de obra das etapas usam o mesmo controle. O saldo e os gráficos só mudam com um pagamento registrado.
- **Pagamentos → Semanas** mostra o calendário completo: total pago, saldo a pagar e saídas conhecidas por semana. Abra **semana → tipo de custo → custo** para ver parcelas, datas e comprovantes. Pagamentos sem data e valores desconhecidos permanecem sinalizados.
- Na ficha do custo, **Programar pagamentos** gera vencimentos semanais ou mensais, com ajuste individual de datas e valores. A programação distribui o valor total do custo; pagamentos já registrados sem vínculo com parcela são abatidos por ordem de vencimento. Pagamentos vinculados a uma parcela quitam primeiro essa parcela.
- Uma parcela paga antecipadamente aparece como saída na data real do pagamento, sem repetir a cobrança na semana prevista. Reprogramar preserva pagamentos, datas, pagadores e anexos; vínculos de parcelas removidas voltam ao abatimento geral do custo.
- O PDF completo inclui o calendário semanal e o detalhamento da quitação. Dados no formato 8, com leitura das versões 1 a 7 e importação de programações sem duplicar registros identificados.
- A assinatura e o pacote Android permanecem os mesmos. Atualize sem desinstalar.

## Versão 1.8.6

- O Painel mostra dois gráficos de pizza: **Quem pagou** e **Como foi pago**, com legendas, valores e percentuais. Cada gráfico soma somente pagamentos registrados, incluindo adiantamentos e parcelas já pagas. Parcelas futuras e pagadores previstos nas compras ficam fora desses totais.
- Variações de maiúsculas e espaços no nome do pagador e nomes equivalentes de métodos são agrupados na apresentação, mantendo os lançamentos originais. **Cartão** permanece separado de **Cartão de crédito** quando o tipo não foi informado.
- Registros sem pagador ou método aparecem como **Não informado**. O PDF completo inclui os dois gráficos e preserva suas demais seções e tabelas.
- Mantém dados, backups e assinatura da versão anterior. Instale como atualização.

## Versão 1.8.5

- Layout com cabeçalho compacto, ícones consistentes, formulários organizados por assunto, abas e tabelas com divisórias leves. Nomes ficam fixos e os totais permanecem abaixo de cada tabela.
- **Pagamentos → Parcelados** separa o total contratado, o **pago efetivo** e o saldo **a pagar** por compra. Apenas pagamentos registrados reduzem o saldo. Pagamentos a maior aparecem na coluna Crédito e não quitam outra compra.
- No cadastro de uma compra parcelada, o campo de pagamento começa vazio para informar somente o valor realmente pago. A quantidade de parcelas descreve a condição comercial e não cria pagamentos.
- O painel e o PDF completo também mostram os valores pagos e pendentes das compras parceladas. Valores desconhecidos continuam como **A definir**, com total parcial.
- Mantém o formato de dados 7 e os backups anteriores, o pacote Android e a assinatura permanente. Instale a atualização mantendo o aplicativo instalado. Os lançamentos anteriores permanecem como foram cadastrados.

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

A coluna **Tipo** identifica **Material** ou **Serviço**. Em compras, escolha a classificação no campo **Tipo de custo**, que também pode ser alterado ao editar o cadastro. As etapas de mão de obra são serviços; os pagamentos e compromissos herdam o tipo da compra ou etapa vinculada. As compras antigas mantêm a classificação de material, usada nas versões anteriores, até serem editadas. Compras de serviços continuam vinculadas ao seu próprio cadastro de compra e os totais financeiros são preservados. Os backups exportados incluem a classificação; os formatos anteriores continuam aceitos.

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

A versão 2.3.1 requer Android 8.0 ou mais recente e atualiza diretamente o **ObraControl 1.7.0 a 2.3.0**, usando a mesma assinatura permanente e o cadastro salvo no aparelho. Abra o instalador e escolha **Atualizar**, mantendo o aplicativo instalado.

Para transferir dados das edições **ObraControl Financeiro** ou **ObraControl Gestão**, abra **Relatório → Exportar backup com anexos** no app anterior. No **ObraControl**, abra **Relatório → Importar dados da obra**, selecione o ZIP e confirme. Confira o cadastro importado antes de desinstalar a edição anterior. Os backups JSON e ZIP das versões anteriores do formato continuam aceitos.

Para começar do zero, cadastre a obra em **Dados da obra**. Quando o cadastro já tem a mesma obra, **Mesclar lançamentos** acrescenta registros, pagadores e anexos ausentes e preserva as edições locais. **Substituir obra** exige confirmação. Para exportar o relatório, use **Relatório → Gerar PDF completo**.

## Compilar

Use JDK 17 e Android SDK 35 com Build Tools 35.0.0. Abra a raiz no Android Studio ou execute:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Em Windows, use `gradlew.bat`. O instalador é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

O workflow do GitHub gera o APK de testes. Para compilar a distribuição assinada, forneça `OBRACONTROL_KEYSTORE_FILE` e `OBRACONTROL_KEYSTORE_PASSWORD`, usando o alias `obracontrol`, e execute `./gradlew :app:assembleRelease`. A chave e sua senha ficam no arquivo privado de assinatura do projeto. As próximas distribuições devem reutilizar essa chave e o identificador `br.com.obracontrol.app` para atualizar o aplicativo instalado.
