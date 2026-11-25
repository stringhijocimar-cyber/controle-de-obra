package br.com.obracontrol

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Editable checklists. Quantities and prices requiring a site survey stay undefined. */
internal fun createWorkPlan(
    type: WorkPlanType, title: String, scope: String, location: String,
    area: BigDecimal?, lossPercent: BigDecimal = BigDecimal.TEN, today: LocalDate = LocalDate.now(),
): WorkPlan {
    fun material(name: String, unit: String = "", quantity: BigDecimal? = null, note: String = "") =
        PlanInput(name = name, category = CostCategory.MATERIAL, unit = unit, quantity = quantity, notes = note)
    fun labor(name: String, unit: String = "", quantity: BigDecimal? = null) =
        PlanInput(name = name, category = CostCategory.LABOR, unit = unit, quantity = quantity)
    fun service(name: String, note: String = "") =
        PlanInput(name = name, category = CostCategory.SERVICE, quantity = BigDecimal.ONE, unit = "serviço", notes = note)
    fun task(name: String, vararg inputs: PlanInput) = PlanService(name = name, items = inputs.toList())
    fun stage(name: String, note: String = "", vararg tasks: PlanService) = PlanStage(name = name, notes = note, services = tasks.toList())
    val stages = when (type) {
        WorkPlanType.CLADDING -> listOf(
            stage("Medição e especificação", "Conferir área líquida, recortes, paginação, uso do ambiente e condição da base.",
                task("Levantamento", service("Medição e especificação do revestimento"))),
            stage("Preparação do ambiente", "Proteger o que será mantido e planejar acessos.",
                task("Proteção", material("Proteções, fitas e consumíveis"), labor("Proteção e organização do local"))),
            stage("Retirada e descarte", "Etapa opcional: excluir se não houver remoção de revestimento existente.",
                task("Remoção", labor("Retirada de revestimento existente", "m²", area), service("Coleta e destinação dos resíduos"))),
            stage("Preparação da base", "Regularização e impermeabilização dependem da condição da base e do ambiente. Ajustar o escopo após avaliação.",
                task("Base", material("Materiais de regularização / tratamento da base", note = "Definir após vistoria."), labor("Preparação e regularização da base", "m²", area))),
            stage("Assentamento e rejuntamento", "Conferir consumos e compatibilidade dos produtos com as orientações do fabricante.",
                task("Revestimento",
                    material("Revestimento com perda estimada", "m²", area?.multiply(BigDecimal.ONE + lossPercent.movePointLeft(2))?.setScale(4, RoundingMode.HALF_UP),
                        "Área × (1 + perda de ${lossPercent.quantityInput()}%). Estimativa editável; conferir paginação e embalagem."),
                    material("Argamassa de assentamento", note = "Quantidade depende do produto, da peça e da base; conferir consumo do fabricante."),
                    material("Rejunte compatível", note = "Quantidade depende da peça e das juntas; conferir consumo do fabricante."),
                    material("Espaçadores e consumíveis"), labor("Assentamento e rejuntamento", "m²", area))),
            stage("Acabamento e entrega", "Conferir juntas, limpeza e itens de aceite.",
                task("Finalização", material("Perfis / acabamentos / selantes, se aplicáveis"), labor("Acabamentos e limpeza final"))),
        )
        WorkPlanType.RENOVATION -> listOf(
            stage("Levantamento e planejamento", "Definir ambientes, medidas, itens mantidos e escopo de cada serviço.",
                task("Projeto e orçamento", service("Levantamento e projeto da reforma"))),
            stage("Preparação e proteção", "Ajustar acessos e logística à ocupação do imóvel.",
                task("Preparação", material("Proteções e consumíveis"), labor("Proteção e organização da obra"))),
            stage("Demolições e descarte", "Manter apenas remoções efetivamente previstas no escopo.",
                task("Retiradas", labor("Remoções previstas no projeto"), service("Transporte e destinação de resíduos"))),
            stage("Adequações de infraestrutura", "Revisar elétrica e hidráulica conforme o levantamento. Não pressupõe intervenção estrutural.",
                task("Elétrica", material("Materiais elétricos conforme projeto"), labor("Adequações elétricas")),
                task("Hidráulica", material("Materiais hidráulicos conforme projeto"), labor("Adequações hidráulicas"))),
            stage("Bases e impermeabilização", "Confirmar necessidade e áreas de aplicação.",
                task("Preparação das superfícies", material("Materiais para base e impermeabilização"), labor("Regularização e impermeabilização"))),
            stage("Revestimentos e acabamentos", "Separar piso, parede, forro e pintura por ambiente ao detalhar.",
                task("Revestimentos", material("Pisos e revestimentos"), material("Argamassas, rejuntes e consumíveis"), labor("Assentamento de revestimentos")),
                task("Pintura e forro", material("Materiais de pintura e forro"), labor("Execução de pintura e forro"))),
            stage("Instalações finais", "Confirmar equipamentos, bancadas e marcenaria incluídos na reforma.",
                task("Montagem", material("Louças, metais, bancadas e equipamentos previstos"), service("Montagem / instalação dos itens"))),
            stage("Limpeza, testes e entrega", "Registrar verificações e pendências de aceite.",
                task("Entrega", service("Limpeza final e testes das instalações"))),
        )
        WorkPlanType.NEW_HOME -> listOf(
            stage("Estudos, projetos e planejamento", "Definir implantação, padrão de acabamento, projetos e orçamento.",
                task("Projetos", service("Levantamentos e projetos necessários"), service("Taxas e despesas de implantação a confirmar"))),
            stage("Preparação do terreno e canteiro", "Quantificar conforme terreno e projeto de implantação.",
                task("Implantação", service("Preparação do terreno e logística"), material("Instalações provisórias e proteção"), labor("Montagem do canteiro"))),
            stage("Fundações", "Quantidades e soluções serão definidas pelo projeto, após avaliação do terreno.",
                task("Execução conforme projeto", material("Materiais de fundação conforme projeto"), labor("Execução das fundações"))),
            stage("Estrutura", "Não dimensionar elementos a partir da área construída; usar o projeto estrutural.",
                task("Estrutura conforme projeto", material("Materiais estruturais conforme projeto"), labor("Execução da estrutura"), service("Equipamentos e apoio de execução"))),
            stage("Vedações e cobertura", "Separar as áreas de alvenaria e de cobertura.",
                task("Vedações", material("Materiais de alvenaria e vedação"), labor("Execução das vedações")),
                task("Cobertura", material("Materiais de cobertura e drenagem"), labor("Montagem da cobertura"))),
            stage("Instalações prediais", "Quantificar pontos, redes e equipamentos pelos projetos.",
                task("Elétrica e hidráulica", material("Materiais elétricos e hidráulicos"), labor("Execução das instalações"))),
            stage("Bases e impermeabilizações", "Definir sistemas e áreas conforme projeto.",
                task("Preparação", material("Materiais de bases e impermeabilização"), labor("Execução das bases e impermeabilizações"))),
            stage("Revestimentos, esquadrias e pintura", "Detalhar por ambiente e padrão de acabamento.",
                task("Acabamentos", material("Revestimentos, esquadrias e materiais de acabamento"), labor("Execução dos acabamentos"))),
            stage("Equipamentos e áreas externas", "Ajustar ao escopo contratado.",
                task("Instalações finais", material("Louças, metais e equipamentos"), service("Instalações finais e obras externas previstas"))),
            stage("Testes, limpeza e entrega", "Controlar verificações, documentação e pendências de entrega.",
                task("Entrega", service("Testes, limpeza e documentação de entrega"))),
        )
        WorkPlanType.CUSTOM -> listOf(stage("Etapa inicial", "Detalhe os serviços e insumos ou importe uma proposta da IA.",
            task("Serviço a detalhar", material("Insumo a especificar"))))
    }
    return WorkPlan(title = title.trim(), type = type, scope = scope.trim(), location = location.trim(), area = area,
        lossPercent = lossPercent, created = today, stages = stages,
        assumptions = listOf("Modelo inicial editável: incluir, excluir e detalhar serviços conforme o escopo real.",
            "Preços não são preenchidos automaticamente. Registrar cotação ou referência com local e data.",
            "Frete, equipamentos, taxas e serviços terceirizados devem ser discriminados sem repetir a mão de obra já incluída."),
        questions = listOf("Quais ambientes, medidas e condições existentes serão considerados?",
            "Qual o padrão de acabamento e o prazo desejado?", "O que a contratação da mão de obra já inclui?"),
    ).also(WorkPlan::validate)
}
