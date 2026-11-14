# ObraControl — Gestão de Obras para Android

Aplicativo Android em Kotlin + Jetpack Compose para controle de obras. A versão **v1.1** foi preparada em ZIP nesta conversa, com dashboard, edição de etapas e compras, backup JSON e relatório PDF multipágina (inclui Gantt, indicadores, materiais discriminados, pagamentos, saldos e riscos).

## Situação de publicação

**Este repositório contém por enquanto a documentação.** O ZIP completo `ObraControl_Android_Completo_v1_1.zip` foi entregue na conversa e precisa ser descompactado e publicado na raiz deste repositório. O APK ainda não foi compilado/testado. Não confundir código preparado com versão publicada ou instalada.

## Dados iniciais — área gourmet

- Previsão parcial: R$ 45.428,10
- Já pago: R$ 20.428,10
- Saldo previsto: R$ 25.000,00
- Materiais futuros: R$ 11.000,00
- Acréscimo declarado: R$ 1.018,60
- Obra iniciada em 03/11/2026; primeira etapa concluída em 13/11/2026, paga em 06/11/2026.

## Funcionalidades e limites

Os dados são armazenados localmente. O PDF é regenerado integralmente a partir do cadastro e apresenta divergências entre totais dos pedidos e linhas transcritas para revisão. A interface ainda não permite editar individualmente todas as linhas detalhadas dos pedidos, e não há validação de compilação ou teste em dispositivo.

## Como publicar e compilar

Descompactar o ZIP e enviar **o conteúdo da pasta ObraControl** para a raiz deste repositório. Depois executar o workflow `Build Android APK` na aba Actions. O APK será um artefato de debug apenas se o build for concluído.
