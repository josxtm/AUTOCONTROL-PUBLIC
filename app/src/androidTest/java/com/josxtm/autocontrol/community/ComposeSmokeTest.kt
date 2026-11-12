package com.josxtm.autocontrol.community

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ComposeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun scroll(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = true))
        return compose.onNodeWithText(text, substring = true)
    }
    @Test fun launchEditDocumentAndDiary() {
        compose.onNodeWithText("AutoControL_tablet_v.1.0 - comp.", substring = true).assertExists()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Procesando en el dispositivo…").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText("Documentos", substring = false)[0].performClick()
        scroll("Nombre del documento").performTextInput("SYN-UI-Document")
        scroll("Texto editable").performTextInput("Synthetic editable text")
        scroll("Guardar documento").performClick()
        // LazyColumn may dispose off-screen status semantics. Bring it into view before waiting.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.waitUntil(15000) { compose.onAllNodesWithText("Documento guardado.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Diario", substring = true).performClick()
        scroll("Nueva entrada").performTextInput("Synthetic UI diary")
        scroll("Añadir entrada").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.waitUntil(15000) { compose.onAllNodesWithText("Entrada guardada.").fetchSemanticsNodes().isNotEmpty() }
        scroll("Synthetic UI diary").assertExists()
    }
}
