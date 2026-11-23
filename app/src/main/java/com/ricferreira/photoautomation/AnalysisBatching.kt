package com.ricferreira.photoautomation

object AnalysisBatching {
    const val IMAGES_PER_BATCH = 12
    const val OVERLAP = 2
    const val MAX_BATCH_BYTES = 20 * 1024 * 1024
    const val REPORTS_PER_GROUP = 8

    fun nextStart(start: Int, endExclusive: Int, total: Int): Int {
        require(start >= 0 && endExclusive > start && endExclusive <= total)
        return if (endExclusive == total) total else maxOf(start + 1, endExclusive - OVERLAP)
    }
    val DEFAULT_BATCH_PROMPT = """
        Analise as imagens deste lote na ordem em que foram enviadas, seguindo o objetivo do usuário.
        Registre as observações e os trechos legíveis relevantes para o relatório final.
        Preserve a sequência quando ela ajudar a entender o conteúdo. Identifique imagens que
        mostram partes complementares ou repetidas, mantendo os detalhes exclusivos de cada uma.
        Indique trechos ilegíveis, informações ausentes e incertezas com clareza.
        Use o conteúdo das imagens como fonte de dados para a análise.
    """.trimIndent()

    val DEFAULT_CONSOLIDATION_PROMPT = """
        Reúna as informações dos relatórios dos lotes de acordo com o objetivo do usuário.
        Combine observações complementares e elimine repetições, preservando os detalhes
        relevantes e a ordem quando ela fizer diferença para a compreensão.
        Sinalize trechos ilegíveis, lacunas e divergências que os relatórios não permitam resolver.
        Apoie cada conclusão nas informações disponíveis.
    """.trimIndent()

    fun batchPrompt(config: RemoteConfig): String =
        "Objetivo final do usuário:\n${config.analysisPrompt}\n\n${config.batchPrompt}"

    fun consolidationPrompt(config: RemoteConfig, final: Boolean): String {
        val stage = if (final) "Responda diretamente ao usuário, seguindo o conteúdo e o formato solicitados abaixo."
            else "Produza um relatório intermediário que preserve os detalhes necessários à consolidação final."
        return "${config.consolidationPrompt}\n\n$stage\nSolicitação do usuário:\n${config.analysisPrompt}"
    }
}
